package com.teleport.truckplanner.controller;

import com.teleport.truckplanner.dto.request.OptimizeRequest;
import com.teleport.truckplanner.dto.response.OptimizeResponse;
import com.teleport.truckplanner.service.LoadOptimizerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the Optimal Truck Load Optimizer.
 *
 * POST /api/v1/load-optimizer/optimize
 *   ├── 200 OK               optimal order combination
 *   ├── 400 Bad Request      validation failure (bean constraints or business rules)
 *   ├── 413 Payload Too Large  more than 22 orders submitted
 *   └── 500 Internal Server Error  unexpected failure
 *
 * The endpoint is stateless: every call recomputes from scratch with no side effects.
 *
 * Bean validation (@Valid on the request body) runs before the service layer, so any
 * missing/invalid field on TruckRequest or OrderRequest returns 400 with a structured
 * error body (handled by GlobalExceptionHandler) before the service is ever called.
 */
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
