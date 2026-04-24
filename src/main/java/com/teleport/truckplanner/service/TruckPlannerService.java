package com.teleport.truckplanner.service;

import com.teleport.truckplanner.dto.LoadPlanRequest;
import com.teleport.truckplanner.dto.LoadPlanResponse;
import com.teleport.truckplanner.exception.PlannerException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates plan creation and retrieval, backed by a Caffeine in-memory cache.
 * The service is completely stateless — no database interaction of any kind.
 */
@Service
public class TruckPlannerService {

    private final OptimizationEngine engine;
    private final Cache planCache;

    public TruckPlannerService(OptimizationEngine engine, CacheManager cacheManager) {
        this.engine = engine;
        this.planCache = Objects.requireNonNull(cacheManager.getCache("loadPlans"),
                "Cache 'loadPlans' not configured");
    }

    /**
     * Compute an optimal load plan and store it in the cache.
     *
     * @return the computed plan (planId can be used to retrieve it later)
     */
    public LoadPlanResponse createPlan(LoadPlanRequest request) {
        String planId = UUID.randomUUID().toString();
        LoadPlanResponse response = engine.optimize(planId, request);
        planCache.put(planId, response);
        return response;
    }

    /**
     * Retrieve a previously computed plan from the cache.
     *
     * @throws PlannerException 404 if the plan has expired or never existed
     */
    public LoadPlanResponse getPlan(String planId) {
        Cache.ValueWrapper wrapper = planCache.get(planId);
        if (wrapper == null) {
            throw new PlannerException(
                    "Plan not found or expired: " + planId,
                    HttpStatus.NOT_FOUND.value());
        }
        return (LoadPlanResponse) wrapper.get();
    }
}