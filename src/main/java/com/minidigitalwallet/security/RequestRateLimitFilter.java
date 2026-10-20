package com.minidigitalwallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Fixed-window request limit per client IP for sensitive auth endpoints, to stop
 * registration spam, credential spraying across usernames, and PIN/refresh abuse.
 * Not a @Component: it is registered only in the security filter chain.
 * Uses the socket address (getRemoteAddr); behind a reverse proxy, configure forwarded
 * headers so this reflects the real client.
 */
public class RequestRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

    private static final Map<String, Integer> LIMITS_PER_MINUTE = Map.of(
        "/api/v1/auth/register", 10,
        "/api/v1/auth/pin", 10,
        "/api/v1/auth/login", 30,
        "/api/v1/auth/refresh", 30
    );

    private final ObjectMapper objectMapper;
    private final Cache<String, Counter> counters = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterWrite(Duration.ofMinutes(1))
        .build();

    public RequestRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Integer limit = LIMITS_PER_MINUTE.get(path);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        Counter counter = counters.get(path + "|" + request.getRemoteAddr(), k -> new Counter());
        if (counter.tryAcquire(System.currentTimeMillis(), limit)) {
            chain.doFilter(request, response);
            return;
        }
        writeTooManyRequests(request, response);
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Retry-After", "60");

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please try again later");
        pd.setType(URI.create("https://minidigitalwallet.com/errors/too-many-requests"));
        pd.setTitle("Too Many Requests");
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("timestamp", Instant.now());

        objectMapper.writeValue(response.getOutputStream(), pd);
    }

    private static final class Counter {
        private long windowStart;
        private int count;

        synchronized boolean tryAcquire(long now, int limit) {
            if (now - windowStart >= WINDOW_MILLIS) {
                windowStart = now;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }
    }
}
