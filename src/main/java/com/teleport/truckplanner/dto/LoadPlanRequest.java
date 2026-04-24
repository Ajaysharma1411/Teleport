package com.teleport.truckplanner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record LoadPlanRequest(
        @NotEmpty(message = "At least one item is required") @Valid List<ItemRequest> items,
        @NotEmpty(message = "At least one truck is required") @Valid List<TruckRequest> trucks
) {}