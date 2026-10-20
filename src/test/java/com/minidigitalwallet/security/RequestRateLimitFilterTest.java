package com.minidigitalwallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRateLimitFilterTest {

    private final RequestRateLimitFilter filter = new RequestRateLimitFilter(new ObjectMapper().findAndRegisterModules());

    private MockHttpServletRequest request(String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void allowsRequestsUpToLimitThenReturns429() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/auth/register", "10.0.0.1"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/auth/register", "10.0.0.1"), blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void limitIsPerClientIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            filter.doFilter(request("/api/v1/auth/register", "10.0.0.1"),
                new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse otherIp = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/auth/register", "10.0.0.2"), otherIp, new MockFilterChain());
        assertThat(otherIp.getStatus()).isEqualTo(200);
    }

    @Test
    void unlistedPathsAreNotLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/wallets", "10.0.0.1"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
