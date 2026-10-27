package com.project.goTruck.repository;

import com.project.goTruck.entity.Feature;
import com.project.goTruck.feature.FeatureRoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FeatureRepository
        extends JpaRepository<Feature, Long> {

    Optional<Feature> findByName(FeatureRoleEnum name);

    @Query("SELECT f FROM Feature f WHERE f.name IN :featureNames")
    List<Feature> findAllByNameIn(List<String> featureNames);
}