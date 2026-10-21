package com.minidigitalwallet.service.fee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context class coordinating fee calculations via Strategy Pattern.
 * Leverages Spring's automatic Map injection (Map&lt;String, FeeStrategy&gt;) to discover
 * all registered strategies at startup without any hardcoded switch/if-else.
 */
@Service
@Slf4j
public class FeeEngine {

    public static final String DEFAULT_STRATEGY = "ZERO";
    private final Map<String, FeeStrategy> strategies = new ConcurrentHashMap<>();

    public FeeEngine(Map<String, FeeStrategy> injectedStrategies) {
        Objects.requireNonNull(injectedStrategies, "injectedStrategies must not be null");
        injectedStrategies.forEach((beanName, strategy) ->
            this.strategies.put(beanName.toUpperCase(Locale.ROOT), strategy)
        );
        log.info("FeeEngine successfully initialized with [{}] strategies: {}",
            strategies.size(), strategies.keySet());
    }

    /**
     * Calculates transaction fee using the specified strategy name.
     * Falls back to "ZERO" strategy if strategyName is null or not found.
     *
     * @param strategyName Name of strategy (e.g., "ZERO", "FLAT", "PERCENTAGE", "TIERED")
     * @param amount       Transaction amount in minor units (cents)
     * @return Fee amount in minor units (cents), guaranteed >= 0
     */
    public long calculateFee(String strategyName, long amount) {
        if (amount <= 0) {
            return 0L;
        }

        String key = (strategyName != null && !strategyName.isBlank())
            ? strategyName.trim().toUpperCase(Locale.ROOT)
            : DEFAULT_STRATEGY;

        FeeStrategy strategy = strategies.getOrDefault(key, strategies.get(DEFAULT_STRATEGY));
        if (strategy == null) {
            log.warn("No fee strategy found for key '{}' and default '{}' is missing. Defaulting to 0.",
                key, DEFAULT_STRATEGY);
            return 0L;
        }

        long fee = strategy.calculateFee(amount);
        return Math.max(0L, fee);
    }

    /**
     * Returns an unmodifiable set of all registered strategy names.
     */
    public Set<String> getAvailableStrategies() {
        return Collections.unmodifiableSet(strategies.keySet());
    }
}
