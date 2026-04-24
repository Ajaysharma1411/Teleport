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

@Service
public class LoadOptimizerService {

    private static final int MAX_ORDERS = 22;

    private final BitmaskDpOptimizer optimizer;

    public LoadOptimizerService(BitmaskDpOptimizer optimizer) {
        this.optimizer = optimizer;
    }
    public OptimizeResponse optimize(OptimizeRequest request) {
        TruckRequest       truck  = request.getTruck();
        List<OrderRequest> orders = request.getOrders() == null
                ? Collections.emptyList()
                : request.getOrders();

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
