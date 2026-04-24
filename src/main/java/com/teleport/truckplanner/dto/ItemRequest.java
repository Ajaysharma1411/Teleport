package com.teleport.truckplanner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ItemRequest(
        @NotBlank(message = "Item id must not be blank") String id,
        @NotBlank(message = "Item name must not be blank") String name,
        @Positive(message = "weightKg must be positive") double weightKg,
        @Positive(message = "volumeM3 must be positive") double volumeM3,
        @PositiveOrZero(message = "priority must be >= 0") int priority
) {}