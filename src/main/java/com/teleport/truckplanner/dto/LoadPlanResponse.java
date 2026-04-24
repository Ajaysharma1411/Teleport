package com.teleport.truckplanner.dto;

import java.time.Instant;
import java.util.List;

public record LoadPlanResponse(
        String planId,
        Instant createdAt,
        List<TruckAssignmentDto> assignments,
        List<ItemRequest> unassignedItems,
        PlanSummary summary
) {}