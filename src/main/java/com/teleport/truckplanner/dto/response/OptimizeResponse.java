package com.teleport.truckplanner.dto.response;

import java.util.List;

/**
 * Response body for POST /api/v1/load-optimizer/optimize.
 *
 * All monetary values are in integer cents to match the input contract.
 * Utilization percentages are rounded to 2 decimal places.
 *
 * The global SNAKE_CASE Jackson strategy serializes Java camelCase fields
 * to snake_case JSON keys automatically:
 *   truckId               → truck_id
 *   selectedOrderIds      → selected_order_ids
 *   totalPayoutCents      → total_payout_cents
 *   totalWeightLbs        → total_weight_lbs
 *   totalVolumeCuft       → total_volume_cuft
 *   utilizationWeightPercent → utilization_weight_percent
 *   utilizationVolumePercent → utilization_volume_percent
 */
public class OptimizeResponse {

    private String       truckId;
    private List<String> selectedOrderIds;
    private long         totalPayoutCents;
    private int          totalWeightLbs;
    private int          totalVolumeCuft;
    private double       utilizationWeightPercent;
    private double       utilizationVolumePercent;

    // Private — callers use the fluent Builder
    private OptimizeResponse() {}

    public String       getTruckId()                  { return truckId; }
    public List<String> getSelectedOrderIds()         { return selectedOrderIds; }
    public long         getTotalPayoutCents()          { return totalPayoutCents; }
    public int          getTotalWeightLbs()            { return totalWeightLbs; }
    public int          getTotalVolumeCuft()           { return totalVolumeCuft; }
    public double       getUtilizationWeightPercent()  { return utilizationWeightPercent; }
    public double       getUtilizationVolumePercent()  { return utilizationVolumePercent; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final OptimizeResponse r = new OptimizeResponse();

        public Builder truckId(String v)                    { r.truckId = v;                    return this; }
        public Builder selectedOrderIds(List<String> v)     { r.selectedOrderIds = v;            return this; }
        public Builder totalPayoutCents(long v)             { r.totalPayoutCents = v;            return this; }
        public Builder totalWeightLbs(int v)                { r.totalWeightLbs = v;              return this; }
        public Builder totalVolumeCuft(int v)               { r.totalVolumeCuft = v;             return this; }
        public Builder utilizationWeightPercent(double v)   { r.utilizationWeightPercent = v;    return this; }
        public Builder utilizationVolumePercent(double v)   { r.utilizationVolumePercent = v;    return this; }

        public OptimizeResponse build() { return r; }
    }
}
