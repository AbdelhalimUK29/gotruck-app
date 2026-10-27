package com.project.goTruck.controller.manager;

import com.project.goTruck.dto.feature.FeatureResponse;
import com.project.goTruck.service.feature.FeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/manager/features")
@RequiredArgsConstructor
@Slf4j
public class FeatureController {

    private final FeatureService featureService;

    /**
     * Retrieves all available static features in the system.
     *
     * Features are global (not company-specific) and represent the complete
     * set of capabilities that can be enabled for companies and assigned to users.
     * This endpoint is available to all authenticated users.
     *
     * @return List of FeatureResponse with id, name, and description
     */
    @GetMapping
    public ResponseEntity<List<FeatureResponse>> getAllFeatures() {
        log.info("Fetching all static features");
        List<FeatureResponse> features = featureService.getAllFeatures();
        return ResponseEntity.ok(features);
    }
}