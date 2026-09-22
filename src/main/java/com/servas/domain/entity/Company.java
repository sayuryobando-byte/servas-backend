package com.servas.domain.entity;

import com.servas.common.constant.DbTables;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = DbTables.COMPANIES)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Provider provider;

    @Column(nullable = false, unique = true, length = 50)
    private String nit;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String address;

    @Column(length = 255)
    private String socialMedia;

    @Column(length = 255)
    private String logoUrl;

    public void updateProfile(String name, String description, String address, String socialMedia, String logoUrl) {
        this.name = name;
        this.description = description;
        this.address = address;
        this.socialMedia = socialMedia;
        this.logoUrl = logoUrl;
    }
}