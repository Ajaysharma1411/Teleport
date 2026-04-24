package com.teleport.truckplanner.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Describes the truck available for this optimization run.
 *
 * Capacities are intentionally integers (no float) to avoid rounding surprises
 * when comparing against accumulated order weights/volumes later in the DP step.
 */
public class TruckRequest {

    @NotBlank(message = "truck.id is required")
    private String id;

    /** Maximum load the truck can carry, in pounds. */
    @NotNull(message = "truck.max_weight_lbs is required")
    @Positive(message = "truck.max_weight_lbs must be positive")
    private Integer maxWeightLbs;

    /** Maximum cargo space, in cubic feet. */
    @NotNull(message = "truck.max_volume_cuft is required")
    @Positive(message = "truck.max_volume_cuft must be positive")
    private Integer maxVolumeCuft;

    public TruckRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getMaxWeightLbs() { return maxWeightLbs; }
    public void setMaxWeightLbs(Integer maxWeightLbs) { this.maxWeightLbs = maxWeightLbs; }

    public Integer getMaxVolumeCuft() { return maxVolumeCuft; }
    public void setMaxVolumeCuft(Integer maxVolumeCuft) { this.maxVolumeCuft = maxVolumeCuft; }
}
