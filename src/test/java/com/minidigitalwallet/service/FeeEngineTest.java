package com.minidigitalwallet.service;

import com.minidigitalwallet.service.fee.FeeEngine;
import com.minidigitalwallet.service.fee.FeeStrategy;
import com.minidigitalwallet.service.fee.FlatFeeStrategy;
import com.minidigitalwallet.service.fee.PercentageFeeStrategy;
import com.minidigitalwallet.service.fee.TieredFeeStrategy;
import com.minidigitalwallet.service.fee.ZeroFeeStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FeeEngine & Strategy Pattern Tests")
class FeeEngineTest {

    private FeeEngine feeEngine;

    @BeforeEach
    void setUp() {
        Map<String, FeeStrategy> strategies = Map.of(
            "ZERO", new ZeroFeeStrategy(),
            "FLAT", new FlatFeeStrategy(100L),
            "PERCENTAGE", new PercentageFeeStrategy(0.015),
            "TIERED", new TieredFeeStrategy(10_000L, 50L)
        );
        feeEngine = new FeeEngine(strategies);
    }

    @Test
    @DisplayName("ZeroFeeStrategy returns 0 cents")
    void testZeroFeeStrategy() {
        assertThat(feeEngine.calculateFee("ZERO", 50_000L)).isEqualTo(0L);
        assertThat(feeEngine.calculateFee("zero", 100_000L)).isEqualTo(0L);
    }

    @Test
    @DisplayName("FlatFeeStrategy charges exact flat fee regardless of amount")
    void testFlatFeeStrategy() {
        assertThat(feeEngine.calculateFee("FLAT", 1_000L)).isEqualTo(100L);
        assertThat(feeEngine.calculateFee("flat", 500_000L)).isEqualTo(100L);
    }

    @Test
    @DisplayName("PercentageFeeStrategy calculates 1.5% with rounding up")
    void testPercentageFeeStrategy() {
        // 10,000 cents * 1.5% = 150 cents
        assertThat(feeEngine.calculateFee("PERCENTAGE", 10_000L)).isEqualTo(150L);
        // 101 cents * 1.5% = 1.515 -> ceil = 2 cents
        assertThat(feeEngine.calculateFee("PERCENTAGE", 101L)).isEqualTo(2L);
    }

    @Test
    @DisplayName("TieredFeeStrategy is free below threshold and flat fee above")
    void testTieredFeeStrategy() {
        // Below or equal to $100 (10,000 cents) -> 0 fee
        assertThat(feeEngine.calculateFee("TIERED", 5_000L)).isEqualTo(0L);
        assertThat(feeEngine.calculateFee("TIERED", 10_000L)).isEqualTo(0L);

        // Above $100 -> 50 cents fee
        assertThat(feeEngine.calculateFee("TIERED", 10_001L)).isEqualTo(50L);
        assertThat(feeEngine.calculateFee("TIERED", 100_000L)).isEqualTo(50L);
    }

    @Test
    @DisplayName("Null or unknown strategy name gracefully defaults to ZERO fee")
    void testFallbackToDefault() {
        assertThat(feeEngine.calculateFee(null, 50_000L)).isEqualTo(0L);
        assertThat(feeEngine.calculateFee("UNKNOWN_POLICY", 50_000L)).isEqualTo(0L);
        assertThat(feeEngine.calculateFee("   ", 50_000L)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Zero or negative transaction amount yields 0 fee")
    void testZeroOrNegativeAmount() {
        assertThat(feeEngine.calculateFee("FLAT", 0L)).isEqualTo(0L);
        assertThat(feeEngine.calculateFee("FLAT", -500L)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Available strategies set contains all registered beans")
    void testAvailableStrategies() {
        assertThat(feeEngine.getAvailableStrategies())
            .containsExactlyInAnyOrder("ZERO", "FLAT", "PERCENTAGE", "TIERED");
    }

    @Test
    @DisplayName("Percentage fee strategy validates rate")
    void testPercentageFeeNegativeRate() {
        assertThatThrownBy(() -> new PercentageFeeStrategy(-0.05))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be negative");
    }
}
