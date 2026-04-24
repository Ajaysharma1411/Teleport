package com.teleport.truckplanner.service;

import com.teleport.truckplanner.dto.request.OrderRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for BitmaskDpOptimizer.
 *
 * These tests verify the core DP algorithm in isolation — no Spring context loaded.
 * All tests pass orders that are already route- and hazmat-compatible (grouping
 * is the responsibility of LoadOptimizerService, not the optimizer itself).
 */
class BitmaskDpOptimizerTest {

    private static final int MAX_WEIGHT = 44_000;  // lbs
    private static final int MAX_VOLUME = 3_000;   // cuft

    private final BitmaskDpOptimizer optimizer = new BitmaskDpOptimizer();

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void emptyOrderList_returnsEmptyResult() {
        var result = optimizer.findOptimal(List.of(), MAX_WEIGHT, MAX_VOLUME);

        assertThat(result.selectedOrderIds()).isEmpty();
        assertThat(result.totalPayoutCents()).isZero();
        assertThat(result.totalWeightLbs()).isZero();
        assertThat(result.totalVolumeCuft()).isZero();
    }

    @Test
    void singleOrder_fitsExactly_isSelected() {
        var result = optimizer.findOptimal(
                List.of(order("o1", 100_00L, 44_000, 3_000)),  // fills truck 100%
                MAX_WEIGHT, MAX_VOLUME);

        assertThat(result.selectedOrderIds()).containsExactly("o1");
        assertThat(result.totalPayoutCents()).isEqualTo(100_00L);
    }

    @Test
    void singleOrder_exceedsWeight_returnsEmpty() {
        var result = optimizer.findOptimal(
                List.of(order("o1", 100_00L, 44_001, 100)),  // 1 lb over limit
                MAX_WEIGHT, MAX_VOLUME);

        assertThat(result.selectedOrderIds()).isEmpty();
        assertThat(result.totalPayoutCents()).isZero();
    }

    @Test
    void singleOrder_exceedsVolume_returnsEmpty() {
        var result = optimizer.findOptimal(
                List.of(order("o1", 100_00L, 100, 3_001)),  // 1 cuft over limit
                MAX_WEIGHT, MAX_VOLUME);

        assertThat(result.selectedOrderIds()).isEmpty();
    }

    // ── Two-order scenarios ───────────────────────────────────────────────────

    @Test
    void twoOrders_bothFit_selectsBoth() {
        // 18 000 + 12 000 = 30 000 lbs (fits),  1 200 + 900 = 2 100 cuft (fits)
        var result = optimizer.findOptimal(
                List.of(
                        order("o1", 250_000L, 18_000, 1_200),
                        order("o2", 180_000L, 12_000, 900)
                ),
                MAX_WEIGHT, MAX_VOLUME);

        assertThat(result.selectedOrderIds()).containsExactlyInAnyOrder("o1", "o2");
        assertThat(result.totalPayoutCents()).isEqualTo(430_000L);
        assertThat(result.totalWeightLbs()).isEqualTo(30_000);
        assertThat(result.totalVolumeCuft()).isEqualTo(2_100);
    }

    @Test
    void twoOrders_onlyOnesFitByWeight_picksHigherPayout() {
        // o1 and o2 together exceed weight; individually both fit.
        // o2 has higher payout → should be selected alone.
        var result = optimizer.findOptimal(
                List.of(
                        order("o1", 100_00L, 30_000, 500),   // payout $100
                        order("o2", 200_00L, 25_000, 500)    // payout $200
                ),
                MAX_WEIGHT,   // 44 000 lbs — together they'd need 55 000
                MAX_VOLUME);

        assertThat(result.selectedOrderIds()).containsExactly("o2");
        assertThat(result.totalPayoutCents()).isEqualTo(200_00L);
    }

    @Test
    void twoOrders_onlyOneFitsByVolume_picksHigherPayout() {
        var result = optimizer.findOptimal(
                List.of(
                        order("o1", 100_00L, 500, 2_000),   // payout $100
                        order("o2", 300_00L, 500, 2_000)    // payout $300 — together exceed volume
                ),
                MAX_WEIGHT,
                MAX_VOLUME);   // 3 000 cuft — together they'd need 4 000

        // Both individually fit; cannot take both → pick higher payout
        assertThat(result.selectedOrderIds()).containsExactly("o2");
        assertThat(result.totalPayoutCents()).isEqualTo(300_00L);
    }

    // ── Problem-statement example ─────────────────────────────────────────────

    /**
     * Reproduces the exact scenario from the assignment spec (non-hazmat group only):
     *   ord-001: $2 500, 18 000 lbs, 1 200 cuft
     *   ord-002: $1 800, 12 000 lbs,   900 cuft
     *   Truck:  44 000 lbs, 3 000 cuft
     *
     * Expected: select both → $4 300 total, 30 000 lbs, 2 100 cuft
     */
    @Test
    void assignmentExample_nonHazmatOrders_selectsBothForMaxPayout() {
        var result = optimizer.findOptimal(
                List.of(
                        order("ord-001", 250_000L, 18_000, 1_200),
                        order("ord-002", 180_000L, 12_000,   900)
                ),
                MAX_WEIGHT, MAX_VOLUME);

        assertThat(result.selectedOrderIds()).containsExactlyInAnyOrder("ord-001", "ord-002");
        assertThat(result.totalPayoutCents()).isEqualTo(430_000L);
        assertThat(result.totalWeightLbs()).isEqualTo(30_000);
        assertThat(result.totalVolumeCuft()).isEqualTo(2_100);
    }

    // ── All orders infeasible ─────────────────────────────────────────────────

    @Test
    void allOrdersExceedCapacity_returnsEmpty() {
        var result = optimizer.findOptimal(
                List.of(
                        order("o1", 100_00L, 50_000, 100),
                        order("o2", 200_00L, 60_000, 100)
                ),
                MAX_WEIGHT, MAX_VOLUME);

        assertThat(result.selectedOrderIds()).isEmpty();
        assertThat(result.totalPayoutCents()).isZero();
    }

    // ── Performance: N = 22 orders must complete well under 800 ms ───────────

    @Test
    void twentyTwoOrders_completesUnder800ms() {
        List<OrderRequest> orders = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            // Each order uses ~2 000 lbs and ~130 cuft so combinations are feasible
            orders.add(order("o" + i, 10_000L + i * 500L, 2_000, 130));
        }

        long start  = System.currentTimeMillis();
        var  result = optimizer.findOptimal(orders, MAX_WEIGHT, MAX_VOLUME);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).as("DP should complete in < 800 ms for N=22").isLessThan(800);
        // Truck holds ~22 orders of 2 000 lbs = 44 000 lbs; greedily all fit
        assertThat(result.selectedOrderIds()).isNotEmpty();
        assertThat(result.totalPayoutCents()).isGreaterThan(0L);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private static OrderRequest order(String id, long payoutCents, int weightLbs, int volumeCuft) {
        OrderRequest o = new OrderRequest();
        o.setId(id);
        o.setPayoutCents(payoutCents);
        o.setWeightLbs(weightLbs);
        o.setVolumeCuft(volumeCuft);
        o.setOrigin("Los Angeles, CA");
        o.setDestination("Dallas, TX");
        o.setPickupDate(LocalDate.of(2025, 12, 5));
        o.setDeliveryDate(LocalDate.of(2025, 12, 9));
        o.setHazmat(false);
        return o;
    }
}
