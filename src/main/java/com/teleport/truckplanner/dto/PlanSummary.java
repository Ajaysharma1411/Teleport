package com.teleport.truckplanner.dto;

public record PlanSummary(
        int totalItems,
        int assignedItems,
        int unassignedItems,
        int trucksUsed,
        int totalTrucks,
        double overallWeightUtilizationPct,
        double overallVolumeUtilizationPct
) {}