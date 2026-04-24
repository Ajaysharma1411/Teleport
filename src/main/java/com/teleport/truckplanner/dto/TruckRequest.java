package com.teleport.truckplanner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Represents a truck available for loading.
 *
 * @param id           unique identifier
 * @param name         human-readable label
 * @param maxWeightKg  maximum payload in kilograms
 * @param maxVolumeM3  maximum cargo volume in cubic metres
 */
public record TruckRequest(
        @NotBlank(message = "Truck id must not be blank") String id,
        @NotBlank(message = "Truck name must not be blank") String name,
        @Positive(message = "maxWeightKg must be positive") double maxWeightKg,
        @Positive(message = "maxVolumeM3 must be positive") double maxVolumeM3
) {}