package com.teleport.truckplanner.service;

import com.teleport.truckplanner.dto.request.OptimizeRequest;
import com.teleport.truckplanner.dto.request.OrderRequest;
import com.teleport.truckplanner.dto.request.TruckRequest;
import com.teleport.truckplanner.dto.response.OptimizeResponse;
import com.teleport.truckplanner.exception.PayloadTooLargeException;
import com.teleport.truckplanner.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the full load-optimization pipeline.
 *
 * Pipeline stages
 * ───────────────
 * 1. Payload-size guard    — reject > 22 orders with HTTP 413.
 * 2. Business validation   — duplicate IDs, incoherent date windows.
 * 3. Compatibility grouping — orders are partitioned by (origin, destination, hazmat).
 *    Only orders in the same partition can share a truck:
 *      • Same headhaul lane (origin → destination, case-insensitive, trimmed).
 *      • Same hazmat class (hazmat orders cannot mix with standard freight).
 * 4. Bitmask DP per group  — BitmaskDpOptimizer finds the optimal subset per group.
 * 5. Global best selection — the highest-payout result across all groups is returned.
 * 6. Response construction — utilisation percentages rounded to 2 decimal places.
 *
 * ── Caching strategy (mentioned, not wired up) ─────────────────────────────────
 * Because the service is stateless and the DP completes in < 100 ms for n=22,
 * caching is only valuable when the same request is repeated.  To add it:
 *
 *   @Cacheable(cacheNames = "optimizations",
 *              key         = "T(java.util.Objects).hash(#request)")
 *
 * This requires equals/hashCode on all request objects.  The trade-off is
 * ~64 MB of cached DP arrays per unique request vs. < 100 ms recompute time —
 * for most logistics patterns recomputation is preferable.
 * ────────────────────────────────────────────────────────────────────────────────
 */
@Service
public class LoadOptimizerService {

    /**
     * Maximum orders per request.
     * The bitmask DP allocates 2^N arrays; N=23 would require 512 MB — impractical.
     * N=22 → 4 M states → ~64 MB, well within Docker container limits.
     */
    private static final int MAX_ORDERS = 22;

    private final BitmaskDpOptimizer optimizer;

    public LoadOptimizerService(BitmaskDpOptimizer optimizer) {
        this.optimizer = optimizer;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public OptimizeResponse optimize(OptimizeRequest request) {
        TruckRequest       truck  = request.getTruck();
        List<OrderRequest> orders = request.getOrders() == null
                ? Collections.emptyList()
                : request.getOrders();

        // Stage 1 — payload size guard (→ 413 via PayloadTooLargeException)
        if (orders.size() > MAX_ORDERS) {
            throw new PayloadTooLargeException(
                    "Request contains " + orders.size() + " orders; maximum supported is " + MAX_ORDERS
                            + " (bitmask DP: 2^" + MAX_ORDERS + " = 4 M states).");
        }

        // Stage 2 — business-rule validation (→ 400 via ValidationException)
        validateOrders(orders);

        // Stage 3 — early exit if nothing to optimise
        if (orders.isEmpty()) {
            return buildResponse(truck, BitmaskDpOptimizer.OptimizationResult.empty());
        }

        // Stage 4 — partition orders into compatibility groups and run DP on each
        Map<String, List<OrderRequest>> groups = groupByCompatibility(orders);

        BitmaskDpOptimizer.OptimizationResult best = BitmaskDpOptimizer.OptimizationResult.empty();
        for (List<OrderRequest> group : groups.values()) {
            BitmaskDpOptimizer.OptimizationResult candidate =
                    optimizer.findOptimal(group, truck.getMaxWeightLbs(), truck.getMaxVolumeCuft());

            // Keep whichever group yields higher total payout
            if (candidate.totalPayoutCents() > best.totalPayoutCents()) {
                best = candidate;
            }
        }

        // Stage 5 — build the HTTP response
        return buildResponse(truck, best);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Validates business rules that go beyond what Bean Validation annotations
     * can express on the DTO level.
     */
    private void validateOrders(List<OrderRequest> orders) {
        Set<String> seenIds = new HashSet<>();
        for (OrderRequest o : orders) {
            // Duplicate IDs: impossible to distinguish in results → reject
            if (!seenIds.add(o.getId())) {
                throw new ValidationException("Duplicate order id: '" + o.getId() + "'");
            }
            // Incoherent time window: pickup after delivery is a data error
            if (o.getPickupDate().isAfter(o.getDeliveryDate())) {
                throw new ValidationException(
                        "Order '" + o.getId() + "': pickup_date (" + o.getPickupDate()
                                + ") must not be after delivery_date (" + o.getDeliveryDate() + ").");
            }
        }
    }

    /**
     * Partitions orders by their compatibility key:
     *   normalised(origin) + "→" + normalised(destination) + "|" + hazmat-class
     *
     * Normalisation: lowercase + trim.  This ensures "Los Angeles, CA" and
     * "los angeles, CA " are treated as the same origin city.
     *
     * Hazmat class: "HAZ" vs "STD".  Hazmat freight must be isolated — carrying
     * hazmat together with standard freight violates DOT regulations.
     */
    private Map<String, List<OrderRequest>> groupByCompatibility(List<OrderRequest> orders) {
        return orders.stream().collect(Collectors.groupingBy(o ->
                normalise(o.getOrigin())
                        + "→" + normalise(o.getDestination())
                        + "|" + (o.isHazmat() ? "HAZ" : "STD")
        ));
    }

    private String normalise(String city) {
        return city.trim().toLowerCase(Locale.ENGLISH);
    }

    private OptimizeResponse buildResponse(TruckRequest truck,
                                            BitmaskDpOptimizer.OptimizationResult result) {
        // Utilisation = (used / max) × 100, rounded to 2 decimal places
        double weightUtil = result.totalWeightLbs() * 100.0 / truck.getMaxWeightLbs();
        double volumeUtil = result.totalVolumeCuft() * 100.0 / truck.getMaxVolumeCuft();

        return OptimizeResponse.builder()
                .truckId(truck.getId())
                .selectedOrderIds(result.selectedOrderIds())
                .totalPayoutCents(result.totalPayoutCents())
                .totalWeightLbs(result.totalWeightLbs())
                .totalVolumeCuft(result.totalVolumeCuft())
                .utilizationWeightPercent(round2(weightUtil))
                .utilizationVolumePercent(round2(volumeUtil))
                .build();
    }

    /** Rounds a double to exactly 2 decimal places using integer arithmetic. */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
