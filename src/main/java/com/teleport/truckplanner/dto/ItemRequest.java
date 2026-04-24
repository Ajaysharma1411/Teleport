package com.teleport.truckplanner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Represents a single item to be loaded onto a truck.
 *
 * @param id         unique identifier for the item
 * @param name       human-readable label
 * @param weightKg   gross weight in kilograms (must be > 0)
 * @param volumeM3   volume in cubic metres (must be > 0)
 * @param priority   loading priority — higher value = loaded first (0 = lowest)
 */
public record ItemRequest(
        @NotBlank(message = "Item id must not be blank") String id,
        @NotBlank(message = "Item name must not be blank") String name,
        @Positive(message = "weightKg must be positive") double weightKg,
        @Positive(message = "volumeM3 must be positive") double volumeM3,
        @PositiveOrZero(message = "priority must be >= 0") int priority
) {}