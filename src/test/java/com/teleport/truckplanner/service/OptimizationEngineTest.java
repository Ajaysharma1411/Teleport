package com.teleport.truckplanner.service;

import com.teleport.truckplanner.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizationEngineTest {

    private final OptimizationEngine engine = new OptimizationEngine();

    @Test
    void allItemsFitInOneTruck() {
        var request = new LoadPlanRequest(
                List.of(
                        new ItemRequest("i1", "Box A", 100.0, 2.0, 1),
                        new ItemRequest("i2", "Box B", 200.0, 3.0, 1)
                ),
                List.of(new TruckRequest("t1", "Truck A", 500.0, 10.0))
        );

        LoadPlanResponse resp = engine.optimize("plan-1", request);

        assertThat(resp.unassignedItems()).isEmpty();
        assertThat(resp.assignments()).hasSize(1);
        assertThat(resp.assignments().get(0).items()).hasSize(2);
        assertThat(resp.summary().assignedItems()).isEqualTo(2);
    }

    @Test
    void itemExceedingWeightCapacityIsUnassigned() {
        var request = new LoadPlanRequest(
                List.of(new ItemRequest("i1", "Heavy Box", 600.0, 2.0, 1)),
                List.of(new TruckRequest("t1", "Truck A", 500.0, 10.0))
        );

        LoadPlanResponse resp = engine.optimize("plan-2", request);

        assertThat(resp.unassignedItems()).hasSize(1);
        assertThat(resp.unassignedItems().get(0).id()).isEqualTo("i1");
        assertThat(resp.assignments()).isEmpty();
    }

    @Test
    void itemExceedingVolumeCapacityIsUnassigned() {
        var request = new LoadPlanRequest(
                List.of(new ItemRequest("i1", "Bulky Box", 100.0, 15.0, 1)),
                List.of(new TruckRequest("t1", "Truck A", 500.0, 10.0))
        );

        LoadPlanResponse resp = engine.optimize("plan-3", request);

        assertThat(resp.unassignedItems()).hasSize(1);
    }

    @Test
    void highPriorityItemLoadedWhenCapacityIsLimited() {
        // Truck fits exactly one of the two items.
        // item-high has higher priority → should be assigned.
        var request = new LoadPlanRequest(
                List.of(
                        new ItemRequest("low",  "Low priority",  400.0, 2.0, 1),
                        new ItemRequest("high", "High priority", 400.0, 2.0, 10)
                ),
                List.of(new TruckRequest("t1", "Truck A", 500.0, 10.0))
        );

        LoadPlanResponse resp = engine.optimize("plan-4", request);

        assertThat(resp.assignments().get(0).items())
                .anyMatch(i -> i.id().equals("high"));
        assertThat(resp.unassignedItems())
                .anyMatch(i -> i.id().equals("low"));
    }

    @Test
    void itemsDistributedAcrossMultipleTrucks() {
        var request = new LoadPlanRequest(
                List.of(
                        new ItemRequest("i1", "Box A", 450.0, 2.0, 1),
                        new ItemRequest("i2", "Box B", 450.0, 2.0, 1)
                ),
                List.of(
                        new TruckRequest("t1", "Truck A", 500.0, 10.0),
                        new TruckRequest("t2", "Truck B", 500.0, 10.0)
                )
        );

        LoadPlanResponse resp = engine.optimize("plan-5", request);

        assertThat(resp.unassignedItems()).isEmpty();
        assertThat(resp.assignments()).hasSize(2);
        assertThat(resp.summary().trucksUsed()).isEqualTo(2);
    }

    @Test
    void utilizationCalculatedCorrectly() {
        var request = new LoadPlanRequest(
                List.of(new ItemRequest("i1", "Box", 250.0, 5.0, 1)),
                List.of(new TruckRequest("t1", "Truck", 500.0, 10.0))
        );

        LoadPlanResponse resp = engine.optimize("plan-6", request);

        TruckAssignmentDto assignment = resp.assignments().get(0);
        assertThat(assignment.weightUtilizationPct()).isEqualTo(50.0);
        assertThat(assignment.volumeUtilizationPct()).isEqualTo(50.0);
        assertThat(resp.summary().overallWeightUtilizationPct()).isEqualTo(50.0);
    }

    @Test
    void emptyTrucksNotIncludedInAssignments() {
        var request = new LoadPlanRequest(
                List.of(new ItemRequest("i1", "Box", 100.0, 1.0, 1)),
                List.of(
                        new TruckRequest("t1", "Truck A", 500.0, 10.0),
                        new TruckRequest("t2", "Truck B", 500.0, 10.0)
                )
        );

        LoadPlanResponse resp = engine.optimize("plan-7", request);

        // Item fits in one truck; the other should not appear in assignments
        assertThat(resp.assignments()).hasSize(1);
        assertThat(resp.summary().trucksUsed()).isEqualTo(1);
        assertThat(resp.summary().totalTrucks()).isEqualTo(2);
    }
}