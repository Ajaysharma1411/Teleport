package com.teleport.truckplanner.controller;

import com.teleport.truckplanner.dto.request.OptimizeRequest;
import com.teleport.truckplanner.dto.response.OptimizeResponse;
import com.teleport.truckplanner.service.LoadOptimizerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/load-optimizer")
public class LoadOptimizerController {

    private final LoadOptimizerService service;

    public LoadOptimizerController(LoadOptimizerService service) {
        this.service = service;
    }

    /**
     * Accepts a truck specification and up to 22 candidate orders.
     * Returns the revenue-maximising subset that fits within the truck's
     * weight and volume limits, while respecting route and hazmat constraints.
     *
     * @param request validated by @Valid before hitting service layer
     * @return 200 with optimal selection details
     */
    @PostMapping("/optimize")
    public ResponseEntity<OptimizeResponse> optimize(@Valid @RequestBody OptimizeRequest request) {
        OptimizeResponse response = service.optimize(request);
        return ResponseEntity.ok(response);
    }
}
