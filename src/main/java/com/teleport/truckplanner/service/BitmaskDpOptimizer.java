package com.teleport.truckplanner.service;

import com.teleport.truckplanner.dto.request.OrderRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class BitmaskDpOptimizer {

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

            int lsbIndex = Integer.numberOfTrailingZeros(mask);
            int prev     = mask ^ (1 << lsbIndex);   // mask with the lsb cleared

            if (dpPayout[prev] < 0L) continue;

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
