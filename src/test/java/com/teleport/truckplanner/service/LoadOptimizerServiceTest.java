package com.teleport.truckplanner.service;

import com.teleport.truckplanner.dto.request.OptimizeRequest;
import com.teleport.truckplanner.dto.request.OrderRequest;
import com.teleport.truckplanner.dto.request.TruckRequest;
import com.teleport.truckplanner.dto.response.OptimizeResponse;
import com.teleport.truckplanner.exception.PayloadTooLargeException;
import com.teleport.truckplanner.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadOptimizerServiceTest {

    private final LoadOptimizerService service =
            new LoadOptimizerService(new BitmaskDpOptimizer());

    @Test
    void emptyOrdersList_returnsEmptySelection() {
        OptimizeResponse response = service.optimize(request(truck44k(), List.of()));

        assertThat(response.getSelectedOrderIds()).isEmpty();
        assertThat(response.getTotalPayoutCents()).isZero();
        assertThat(response.getUtilizationWeightPercent()).isZero();
        assertThat(response.getUtilizationVolumePercent()).isZero();
        assertThat(response.getTruckId()).isEqualTo("truck-123");
    }

    @Test
    void nullOrdersList_treatedAsEmpty() {
        OptimizeRequest req = new OptimizeRequest();
        req.setTruck(truck44k());
        req.setOrders(null);   // service must handle null gracefully

        OptimizeResponse response = service.optimize(req);
        assertThat(response.getSelectedOrderIds()).isEmpty();
    }


    @Test
    void assignmentExample_selectsNonHazmatGroupAsHigherPayout() {
        List<OrderRequest> orders = List.of(
                order("ord-001", 250_000L, 18_000, 1_200, false),
                order("ord-002", 180_000L, 12_000,   900, false),
                order("ord-003", 320_000L, 30_000, 1_800, true)
        );

        OptimizeResponse response = service.optimize(request(truck44k(), orders));

        assertThat(response.getSelectedOrderIds()).containsExactlyInAnyOrder("ord-001", "ord-002");
        assertThat(response.getTotalPayoutCents()).isEqualTo(430_000L);
        assertThat(response.getTotalWeightLbs()).isEqualTo(30_000);
        assertThat(response.getTotalVolumeCuft()).isEqualTo(2_100);
        // utilisation: 30 000 / 44 000 = 68.18%,  2 100 / 3 000 = 70.0%
        assertThat(response.getUtilizationWeightPercent()).isEqualTo(68.18);
        assertThat(response.getUtilizationVolumePercent()).isEqualTo(70.0);
    }

    // ── Hazmat isolation ──────────────────────────────────────────────────────

    @Test
    void hazmatOrdersCannotMixWithStandard_selectedSeparately() {
        // Hazmat order alone pays more than all non-hazmat together
        List<OrderRequest> orders = List.of(
                order("haz", 999_999L, 10_000, 500, true),   // hazmat, huge payout
                order("std", 100_00L,  10_000, 500, false)   // non-hazmat, small payout
        );

        OptimizeResponse response = service.optimize(request(truck44k(), orders));

        // Only the hazmat order should be selected (higher payout, isolated group)
        assertThat(response.getSelectedOrderIds()).containsExactly("haz");
        assertThat(response.getTotalPayoutCents()).isEqualTo(999_999L);
    }

    // ── Route grouping ────────────────────────────────────────────────────────

    @Test
    void ordersOnDifferentRoutes_onlyBestRouteGroupSelected() {
        // Route A (LA→Dallas): combined payout $500
        // Route B (LA→Chicago): single order payout $600 — should win
        List<OrderRequest> orders = List.of(
                orderOnRoute("a1", 200_00L, 10_000, 500, "Los Angeles, CA", "Dallas, TX"),
                orderOnRoute("a2", 300_00L, 10_000, 500, "Los Angeles, CA", "Dallas, TX"),
                orderOnRoute("b1", 600_00L, 10_000, 500, "Los Angeles, CA", "Chicago, IL")
        );

        OptimizeResponse response = service.optimize(request(truck44k(), orders));

        assertThat(response.getSelectedOrderIds()).containsExactly("b1");
        assertThat(response.getTotalPayoutCents()).isEqualTo(600_00L);
    }

    @Test
    void routeMatchingIsCaseInsensitiveAndTrimmed() {
        // "Los Angeles, CA" and "los angeles, ca " must be treated as the same origin
        List<OrderRequest> orders = List.of(
                orderOnRoute("o1", 100_00L, 10_000, 500, "Los Angeles, CA",  "Dallas, TX"),
                orderOnRoute("o2", 200_00L, 10_000, 500, "los angeles, ca ", "Dallas, TX")
        );

        OptimizeResponse response = service.optimize(request(truck44k(), orders));

        assertThat(response.getSelectedOrderIds()).containsExactlyInAnyOrder("o1", "o2");
    }


    @Test
    void duplicateOrderIds_throwsValidationException() {
        List<OrderRequest> orders = List.of(
                order("dup", 100_00L, 1_000, 100, false),
                order("dup", 200_00L, 2_000, 200, false)   // same id
        );

        assertThatThrownBy(() -> service.optimize(request(truck44k(), orders)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("dup");
    }

    @Test
    void pickupDateAfterDeliveryDate_throwsValidationException() {
        OrderRequest bad = order("o1", 100_00L, 1_000, 100, false);
        bad.setPickupDate(LocalDate.of(2025, 12, 10));
        bad.setDeliveryDate(LocalDate.of(2025, 12, 5));   // pickup > delivery

        assertThatThrownBy(() -> service.optimize(request(truck44k(), List.of(bad))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("o1");
    }


    @Test
    void moreThan22Orders_throwsPayloadTooLargeException() {
        List<OrderRequest> orders = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            orders.add(order("o" + i, 100_00L, 1_000, 100, false));
        }

        assertThatThrownBy(() -> service.optimize(request(truck44k(), orders)))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void exactly22Orders_accepted() {
        List<OrderRequest> orders = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            orders.add(order("o" + i, 100_00L, 1_000, 100, false));
        }
        assertThat(service.optimize(request(truck44k(), orders))).isNotNull();
    }


    @Test
    void utilizationRoundedToTwoDecimalPlaces() {
        OptimizeResponse response = service.optimize(
                request(truck44k(), List.of(order("o1", 100_00L, 10_000, 300, false))));

        assertThat(response.getUtilizationWeightPercent()).isEqualTo(22.73);
        assertThat(response.getUtilizationVolumePercent()).isEqualTo(10.0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static TruckRequest truck44k() {
        TruckRequest t = new TruckRequest();
        t.setId("truck-123");
        t.setMaxWeightLbs(44_000);
        t.setMaxVolumeCuft(3_000);
        return t;
    }

    private static OptimizeRequest request(TruckRequest truck, List<OrderRequest> orders) {
        OptimizeRequest r = new OptimizeRequest();
        r.setTruck(truck);
        r.setOrders(orders);
        return r;
    }

    private static OrderRequest order(String id, long payoutCents, int weight, int volume,
                                       boolean hazmat) {
        return orderOnRoute(id, payoutCents, weight, volume, "Los Angeles, CA", "Dallas, TX",
                hazmat);
    }

    private static OrderRequest orderOnRoute(String id, long payoutCents, int weight, int volume,
                                              String origin, String destination) {
        return orderOnRoute(id, payoutCents, weight, volume, origin, destination, false);
    }

    private static OrderRequest orderOnRoute(String id, long payoutCents, int weight, int volume,
                                              String origin, String destination, boolean hazmat) {
        OrderRequest o = new OrderRequest();
        o.setId(id);
        o.setPayoutCents(payoutCents);
        o.setWeightLbs(weight);
        o.setVolumeCuft(volume);
        o.setOrigin(origin);
        o.setDestination(destination);
        o.setPickupDate(LocalDate.of(2025, 12, 5));
        o.setDeliveryDate(LocalDate.of(2025, 12, 9));
        o.setHazmat(hazmat);
        return o;
    }
}
