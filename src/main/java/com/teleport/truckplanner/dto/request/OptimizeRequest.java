package com.teleport.truckplanner.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class OptimizeRequest {

    @NotNull(message = "truck is required")
    @Valid
    private TruckRequest truck;

    @NotNull(message = "orders array is required (pass [] for an empty run)")
    private List<@Valid OrderRequest> orders;

    public OptimizeRequest() {}

    public TruckRequest getTruck() { return truck; }
    public void setTruck(TruckRequest truck) { this.truck = truck; }

    public List<OrderRequest> getOrders() { return orders; }
    public void setOrders(List<OrderRequest> orders) { this.orders = orders; }
}