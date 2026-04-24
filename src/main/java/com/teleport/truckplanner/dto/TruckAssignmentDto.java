package com.teleport.truckplanner.dto;

import java.util.List;

public record TruckAssignmentDto(
        TruckRequest truck,
        List<ItemRequest> items,
        double totalWeightKg,
        double totalVolumeM3,
        double weightUtilizationPct,
        double volumeUtilizationPct
) {}