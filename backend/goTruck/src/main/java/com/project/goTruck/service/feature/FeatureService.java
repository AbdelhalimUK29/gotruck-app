package com.project.goTruck.service.feature;

import com.project.goTruck.dto.feature.FeatureResponse;
import com.project.goTruck.entity.Feature;
import com.project.goTruck.repository.FeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureRepository featureRepository;

    /**
     * Retrieves all available static features in the system.
     *
     * Features are global (not company-specific) and represent the complete
     * set of capabilities that can be enabled for companies and assigned to users.
     * Results are cached in Redis for 24 hours since features rarely change.
     *
     * @return List of FeatureResponse with id and name
     */
    @Cacheable(value = "features", key = "'all'")
    @Transactional(readOnly = true)
    public List<FeatureResponse> getAllFeatures() {
        log.info("Fetching all static features from database (cache miss)");

        List<Feature> features = featureRepository.findAll();

        return features.stream()
                .map(f -> new FeatureResponse(f.getId(), f.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Evicts the features cache.
     * Useful when features are added/removed/updated in the database.
     */
    @CacheEvict(value = "features", allEntries = true)
    public void evictFeaturesCache() {
        log.info("Evicted features cache");
    }
}