package com.teleport.truckplanner.service;

import com.teleport.truckplanner.dto.request.OrderRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │           BITMASK DYNAMIC PROGRAMMING — OPTIMAL LOAD SELECTOR          │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Problem
 * ───────
 * Given N orders (N ≤ 22) and a truck with two capacity limits (weight + volume),
 * find the subset of orders that maximises total payout_cents while satisfying
 * BOTH limits.  This is a two-constraint 0/1 knapsack problem.
 *
 * Why Bitmask DP?
 * ───────────────
 * A classic DP knapsack is pseudo-polynomial in the capacity values (impractical
 * here since max_weight can be 44 000 lbs × 22 items ≈ 1 M states per dimension).
 * With N ≤ 22, iterating over all 2^N subsets is tractable:
 *   2^22 = 4 194 304 — runs in < 100 ms on a modern JVM.
 *
 * Core insight
 * ────────────
 * Each integer `mask` (0 … 2^N−1) uniquely identifies a subset of N orders via
 * its binary representation:  bit i set ⟺ order i is included.
 *
 * Incremental aggregation (O(2^N) time, no inner loop over N):
 * ─────────────────────────────────────────────────────────────
 * For any mask ≠ 0:
 *   lsb   = lowest set bit of mask             (the "new" order just added)
 *   prev  = mask ^ (1 << lsb)                  (same subset minus lsb)
 *   weight[mask] = weight[prev] + order[lsb].weight
 *   volume[mask] = volume[prev] + order[lsb].volume
 *   payout[mask] = payout[prev] + order[lsb].payout
 *
 * Because masks are iterated in ascending order, `prev` is always computed
 * before `mask`.
 *
 * Monotonic pruning (key performance win):
 * ─────────────────────────────────────────
 * All weights and volumes are ≥ 0, so if subset `prev` is infeasible
 * (exceeds a capacity), every SUPERSET of `prev` is also infeasible.
 * Infeasible masks are marked with payout = −1 and skipped immediately.
 *
 * Memory layout (N = 22):
 * ───────────────────────
 *   long[] dpPayout   2^22 × 8 bytes = 32 MB
 *   int[]  dpWeight   2^22 × 4 bytes = 16 MB
 *   int[]  dpVolume   2^22 × 4 bytes = 16 MB
 *   ──────────────────────────────────────────
 *   Total             ~ 64 MB per invocation (GC-eligible immediately after return)
 */
@Component
public class BitmaskDpOptimizer {

    /**
     * Immutable value object carrying the result of one optimization run.
     * Uses a Java 17 record for conciseness and automatic equals/hashCode.
     */
    public record OptimizationResult(
            List<String> selectedOrderIds,
            long         totalPayoutCents,
            int          totalWeightLbs,
            int          totalVolumeCuft
    ) {
        /** Sentinel: no orders selected, zero payout — the safe fallback. */
        public static OptimizationResult empty() {
            return new OptimizationResult(List.of(), 0L, 0, 0);
        }
    }

    /**
     * Finds the revenue-maximising feasible subset of the supplied orders.
     *
     * All orders in this list must already be route- and hazmat-compatible
     * (that filtering is the caller's responsibility — see LoadOptimizerService).
     *
     * @param orders    pre-filtered candidate orders (same lane + hazmat class)
     * @param maxWeight truck's weight capacity in lbs
     * @param maxVolume truck's volume capacity in cuft
     * @return best feasible selection; empty result if no order individually fits
     */
    public OptimizationResult findOptimal(List<OrderRequest> orders,
                                          int maxWeight,
                                          int maxVolume) {
        int n = orders.size();
        if (n == 0) return OptimizationResult.empty();

        // 1 << n = total number of subsets (including the empty set at index 0)
        int totalMasks = 1 << n;

        // ── DP tables indexed by bitmask ──────────────────────────────────────
        // Sentinel -1 in dpPayout means "this subset is infeasible".
        // All other indices start at -1; feasible ones get overwritten as we go.
        long[] dpPayout = new long[totalMasks];
        int[]  dpWeight = new int[totalMasks];
        int[]  dpVolume = new int[totalMasks];
        Arrays.fill(dpPayout, -1L);

        // Base case: empty set (mask = 0) is always feasible with zero cost
        dpPayout[0] = 0L;
        // dpWeight[0] and dpVolume[0] are 0 by default (Java array initialisation)

        long bestPayout = 0L;
        int  bestMask   = 0;    // fallback: empty selection

        // ── Main sweep: ascending mask order guarantees prev < mask ───────────
        for (int mask = 1; mask < totalMasks; mask++) {

            // Decompose mask into its lowest-set-bit order and the remaining subset.
            // numberOfTrailingZeros is a single CPU instruction on modern hardware.
            int lsbIndex = Integer.numberOfTrailingZeros(mask);
            int prev     = mask ^ (1 << lsbIndex);   // mask with the lsb cleared

            // ── Monotonic pruning ────────────────────────────────────────────
            // If prev is infeasible, adding any order to it only increases weight/
            // volume → mask must also be infeasible.  Skip without writing to DP.
            if (dpPayout[prev] < 0L) continue;

            // ── Feasibility check ────────────────────────────────────────────
            OrderRequest order = orders.get(lsbIndex);
            int newWeight = dpWeight[prev] + order.getWeightLbs();
            int newVolume = dpVolume[prev] + order.getVolumeCuft();

            if (newWeight <= maxWeight && newVolume <= maxVolume) {
                // Record this feasible subset
                dpPayout[mask] = dpPayout[prev] + order.getPayoutCents();
                dpWeight[mask] = newWeight;
                dpVolume[mask] = newVolume;

                // ── Optimality tracking ──────────────────────────────────────
                if (dpPayout[mask] > bestPayout) {
                    bestPayout = dpPayout[mask];
                    bestMask   = mask;
                }
            }
            // else: dpPayout[mask] stays -1 (infeasible — pruned in future iterations)
        }

        // ── Reconstruct selected order IDs from the winning bitmask ──────────
        // Iterate through all N bit positions; collect IDs for set bits.
        List<String> selectedIds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if ((bestMask & (1 << i)) != 0) {
                selectedIds.add(orders.get(i).getId());
            }
        }

        return new OptimizationResult(
                selectedIds,
                dpWeight[bestMask] == 0 && dpVolume[bestMask] == 0 && bestMask == 0
                        ? 0L : dpPayout[bestMask],  // guard: empty selection has payout 0
                dpWeight[bestMask],
                dpVolume[bestMask]
        );
    }
}
