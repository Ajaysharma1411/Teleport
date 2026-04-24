package com.teleport.truckplanner.service;

import com.teleport.truckplanner.dto.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core bin-packing engine using the Best Fit Decreasing (BFD) heuristic.
 *
 * <p>Algorithm overview:
 * <ol>
 *   <li>Sort items by priority DESC, then by combined normalised size DESC.</li>
 *   <li>For each item, scan all trucks and pick the one that (a) still has capacity
 *       and (b) has the <em>smallest</em> remaining combined capacity after the item
 *       would be placed — the "tightest fit" that avoids fragmenting large trucks.</li>
 *   <li>Items that fit in no truck are collected as unassigned.</li>
 * </ol>
 *
 * <p>Complexity: O(I × T) where I = number of items, T = number of trucks.
 * For typical logistics inputs this is negligible.
 */
@Component
public class OptimizationEngine {

    public LoadPlanResponse optimize(String planId, LoadPlanRequest request) {
        List<ItemRequest> sorted = sortItems(request.items());
        List<TruckState> states = request.trucks().stream()
                .map(TruckState::new)
                .collect(Collectors.toList());

        List<ItemRequest> unassigned = new ArrayList<>();

        for (ItemRequest item : sorted) {
            findBestFit(item, states)
                    .ifPresentOrElse(
                            t -> t.assign(item),
                            () -> unassigned.add(item));
        }

        List<TruckAssignmentDto> assignments = states.stream()
                .filter(s -> !s.assignedItems.isEmpty())
                .map(this::toDto)
                .collect(Collectors.toList());

        return new LoadPlanResponse(
                planId,
                Instant.now(),
                assignments,
                Collections.unmodifiableList(unassigned),
                buildSummary(request, assignments, unassigned));
    }

    // -------------------------------------------------------------------------
    // Sorting
    // -------------------------------------------------------------------------

    private List<ItemRequest> sortItems(List<ItemRequest> items) {
        double maxW = items.stream().mapToDouble(ItemRequest::weightKg).max().orElse(1.0);
        double maxV = items.stream().mapToDouble(ItemRequest::volumeM3).max().orElse(1.0);

        return items.stream()
                .sorted(Comparator
                        // Higher priority first
                        .comparingInt(ItemRequest::priority).reversed()
                        // Within same priority, larger items first (better packing)
                        .thenComparingDouble((ItemRequest i) ->
                                i.weightKg() / maxW + i.volumeM3() / maxV).reversed())
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Best-Fit selection
    // -------------------------------------------------------------------------

    private Optional<TruckState> findBestFit(ItemRequest item, List<TruckState> trucks) {
        TruckState best = null;
        double bestScore = Double.MAX_VALUE;

        for (TruckState t : trucks) {
            if (!t.canFit(item)) continue;

            // Score = average remaining-capacity fraction AFTER placing the item.
            // Lower score → tighter fit → preferred.
            double wFrac = (t.remainingWeightKg - item.weightKg()) / t.truck.maxWeightKg();
            double vFrac = (t.remainingVolumeM3 - item.volumeM3()) / t.truck.maxVolumeM3();
            double score = (wFrac + vFrac) / 2.0;

            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return Optional.ofNullable(best);
    }

    // -------------------------------------------------------------------------
    // Response builders
    // -------------------------------------------------------------------------

    private TruckAssignmentDto toDto(TruckState s) {
        double usedW = s.truck.maxWeightKg() - s.remainingWeightKg;
        double usedV = s.truck.maxVolumeM3() - s.remainingVolumeM3;
        double wPct  = round2(usedW / s.truck.maxWeightKg() * 100.0);
        double vPct  = round2(usedV / s.truck.maxVolumeM3() * 100.0);
        return new TruckAssignmentDto(
                s.truck,
                List.copyOf(s.assignedItems),
                round2(usedW),
                round2(usedV),
                wPct,
                vPct);
    }

    private PlanSummary buildSummary(LoadPlanRequest req,
                                     List<TruckAssignmentDto> assignments,
                                     List<ItemRequest> unassigned) {
        double totalMaxW = assignments.stream().mapToDouble(a -> a.truck().maxWeightKg()).sum();
        double totalMaxV = assignments.stream().mapToDouble(a -> a.truck().maxVolumeM3()).sum();
        double usedW     = assignments.stream().mapToDouble(TruckAssignmentDto::totalWeightKg).sum();
        double usedV     = assignments.stream().mapToDouble(TruckAssignmentDto::totalVolumeM3).sum();

        return new PlanSummary(
                req.items().size(),
                req.items().size() - unassigned.size(),
                unassigned.size(),
                assignments.size(),
                req.trucks().size(),
                totalMaxW > 0 ? round2(usedW / totalMaxW * 100.0) : 0.0,
                totalMaxV > 0 ? round2(usedV / totalMaxV * 100.0) : 0.0);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // -------------------------------------------------------------------------
    // Mutable truck state (used only during a single optimize() call)
    // -------------------------------------------------------------------------

    static final class TruckState {
        final TruckRequest truck;
        double remainingWeightKg;
        double remainingVolumeM3;
        final List<ItemRequest> assignedItems = new ArrayList<>();

        TruckState(TruckRequest truck) {
            this.truck = truck;
            this.remainingWeightKg = truck.maxWeightKg();
            this.remainingVolumeM3 = truck.maxVolumeM3();
        }

        boolean canFit(ItemRequest item) {
            return item.weightKg() <= remainingWeightKg
                    && item.volumeM3() <= remainingVolumeM3;
        }

        void assign(ItemRequest item) {
            remainingWeightKg -= item.weightKg();
            remainingVolumeM3 -= item.volumeM3();
            assignedItems.add(item);
        }
    }
}