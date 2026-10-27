package com.project.goTruck.mapper.company;

import com.project.goTruck.dto.company.CompanyFeatureResponse;
import com.project.goTruck.entity.CompanyFeature;
import com.project.goTruck.feature.FeatureRoleEnum;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface CompanyFeatureMapper {

    CompanyFeatureMapper INSTANCE = Mappers.getMapper(CompanyFeatureMapper.class);

    @Mapping(source = "feature.id", target = "featureId")
    @Mapping(source = "feature.name", target = "featureName", qualifiedByName = "featureNameToString")
    CompanyFeatureResponse toResponse(CompanyFeature cf);

    default List<CompanyFeatureResponse> toResponseList(List<CompanyFeature> features) {
        return features.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    default Set<CompanyFeatureResponse> toResponseSet(Set<CompanyFeature> features) {
        return features.stream()
                .map(this::toResponse)
                .collect(Collectors.toSet());
    }

    @org.mapstruct.Named("featureNameToString")
    default String featureNameToString(FeatureRoleEnum name) {
        return name != null ? name.getValue() : null;
    }
}