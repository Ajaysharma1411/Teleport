package com.teleport.truckplanner.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Root request body for POST /api/v1/load-optimizer/optimize.
 *
 * Jackson applies the global SNAKE_CASE naming strategy, so the "truck"
 * and "orders" JSON keys map directly to these field names without any
 * extra @JsonProperty annotations.
 */
public class OptimizeRequest {

    @NotNull(message = "truck is required")
    @Valid  // triggers recursive validation of TruckRequest fields
    private TruckRequest truck;

    @NotNull(message = "orders array is required (pass [] for an empty run)")
    private List<@Valid OrderRequest> orders;  // @Valid on each element validates OrderRequest fields

    public OptimizeRequest() {}

    public TruckRequest getTruck() { return truck; }
    public void setTruck(TruckRequest truck) { this.truck = truck; }

    public List<OrderRequest> getOrders() { return orders; }
    public void setOrders(List<OrderRequest> orders) { this.orders = orders; }
}