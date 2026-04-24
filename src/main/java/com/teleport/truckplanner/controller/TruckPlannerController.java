package com.teleport.truckplanner.controller;

import com.teleport.truckplanner.dto.LoadPlanRequest;
import com.teleport.truckplanner.dto.LoadPlanResponse;
import com.teleport.truckplanner.service.TruckPlannerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/plans")
public class TruckPlannerController {

    private final TruckPlannerService service;

    public TruckPlannerController(TruckPlannerService service) {
        this.service = service;
    }

    /**
     * Compute an optimal load plan.
     * Returns HTTP 201 with a Location header pointing to the new plan resource.
     */
    @PostMapping
    public ResponseEntity<LoadPlanResponse> createPlan(@Valid @RequestBody LoadPlanRequest request) {
        LoadPlanResponse response = service.createPlan(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.planId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieve a previously computed plan by its ID.
     * Plans are held in the Caffeine cache for 60 minutes.
     */
    @GetMapping("/{planId}")
    public ResponseEntity<LoadPlanResponse> getPlan(@PathVariable String planId) {
        return ResponseEntity.ok(service.getPlan(planId));
    }
}