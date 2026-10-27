package com.project.goTruck.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 255)
    private String website;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 255)
    private String location;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    /**
     * Current logo object key in the public media bucket
     * (e.g. company/25/logo/<uploadId>.webp). Null until the first
     * logo has been processed. Temporary upload state never lives
     * here — only the current final object key.
     */
    @Column(length = 512)
    private String logo;

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<CompanyFeature> companyFeatures = new HashSet<>();

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Rental> rentals = new HashSet<>();

}