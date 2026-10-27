package com.project.goTruck.entity.user;

import com.project.goTruck.entity.Company;
import com.project.goTruck.entity.UserFeature;
import com.project.goTruck.entity.UserRental;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "keycloak_id"),
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    //TODO ADD A FULLNAME TO SUPPORT THE FILTER USING THE FULL NAME SO THAT THE FILTER MUCH FASTER PLUS INDEXING
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_id", nullable = false)
    private UUID keycloakId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private boolean owner;

    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin;

    @Column(name = "username", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String username;

    @Column(name = "email", nullable = false, length = 255)
    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    @Size(max = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    @Size(max = 100)
    private String lastName;

    @Column(name = "full_name", length = 201)
    @Size(max = 201)
    private String fullName;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserFeature> userFeatures = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserRental> userRentals = new HashSet<>();

    @PrePersist
    @PreUpdate
    private void computeFullName() {
        String fn = firstName != null ? firstName : "";
        String ln = lastName != null ? lastName : "";
        this.fullName = (fn + " " + ln).trim();
    }

}