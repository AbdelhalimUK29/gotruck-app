package com.project.goTruck.dto.rental;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Data
public class RentalFilterDTO {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Min(value = 0, message = "Page must be >= 0")
    private int page = 0;

    @Min(value = 1, message = "Size must be >= 1")
    @Max(value = 100, message = "Size must not exceed 100")
    private int size = 20;

    @Pattern(regexp = "^[a-zA-Z0-9_]+,(asc|desc)$", message = "Sort must be in format 'field,asc' or 'field,desc'")
    private String sort;

    // Company ID is set in controller from session, not from request body
    private Long companyId;

    public Pageable toPageable() {
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = parts[0];
            Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            return PageRequest.of(page, size, Sort.by(direction, property));
        }
        return PageRequest.of(page, size);
    }
}