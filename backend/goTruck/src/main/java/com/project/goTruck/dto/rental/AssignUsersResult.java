package com.project.goTruck.dto.rental;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignUsersResult {
    private List<Long> assigned;
    private List<Long> alreadyAssigned;
    private List<Long> invalidUsers;
}