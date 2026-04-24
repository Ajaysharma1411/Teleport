package com.teleport.truckplanner.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * A single candidate order to be evaluated for loading.
 *
 * Money rule: payout is stored as a 64-bit integer (cents) throughout the entire
 * pipeline.  Using long avoids all floating-point rounding errors that would arise
 * with double (e.g. $2500.00 stored as 250000 cents, not 2500.00).
 *
 * Hazmat note: the JSON key is "is_hazmat" but the Java field is named "hazmat"
 * so that the getter is isHazmat() — a conventional boolean accessor name.
 * @JsonProperty pins the JSON name explicitly, overriding the global SNAKE_CASE
 * strategy for this one field.
 */
public class OrderRequest {

    @NotBlank(message = "order id is required")
    private String id;

    /**
     * Carrier payout for this order in integer cents (e.g. $2500.00 → 250000).
     * Never use float/double for money.
     */
    @NotNull(message = "payout_cents is required")
    @PositiveOrZero(message = "payout_cents must be >= 0")
    private Long payoutCents;

    @NotNull(message = "weight_lbs is required")
    @PositiveOrZero(message = "weight_lbs must be >= 0")
    private Integer weightLbs;

    @NotNull(message = "volume_cuft is required")
    @PositiveOrZero(message = "volume_cuft must be >= 0")
    private Integer volumeCuft;

    @NotBlank(message = "origin is required")
    private String origin;

    @NotBlank(message = "destination is required")
    private String destination;

    /** Earliest date the shipment can be picked up. Must be ≤ deliveryDate. */
    @NotNull(message = "pickup_date is required")
    private LocalDate pickupDate;

    /** Latest date the shipment must be delivered. Must be ≥ pickupDate. */
    @NotNull(message = "delivery_date is required")
    private LocalDate deliveryDate;

    /**
     * Hazmat orders may not share a truck with non-hazmat orders.
     * The @JsonProperty annotations (on field AND setter) ensure Jackson maps
     * the JSON key "is_hazmat" to this field both when reading and writing.
     */
    @JsonProperty("is_hazmat")
    private boolean hazmat;

    public OrderRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getPayoutCents() { return payoutCents; }
    public void setPayoutCents(Long payoutCents) { this.payoutCents = payoutCents; }

    public Integer getWeightLbs() { return weightLbs; }
    public void setWeightLbs(Integer weightLbs) { this.weightLbs = weightLbs; }

    public Integer getVolumeCuft() { return volumeCuft; }
    public void setVolumeCuft(Integer volumeCuft) { this.volumeCuft = volumeCuft; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public LocalDate getPickupDate() { return pickupDate; }
    public void setPickupDate(LocalDate pickupDate) { this.pickupDate = pickupDate; }

    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }

    public boolean isHazmat() { return hazmat; }

    @JsonProperty("is_hazmat")
    public void setHazmat(boolean hazmat) { this.hazmat = hazmat; }
}
