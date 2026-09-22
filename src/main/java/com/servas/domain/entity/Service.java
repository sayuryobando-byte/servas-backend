package com.servas.domain.entity;

import com.servas.common.constant.DbTables;
import com.servas.domain.enumeration.Modality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = DbTables.SERVICES)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn
    private Comuna comuna;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Modality modality;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String recommendations;

    @Column
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean isActive = Boolean.TRUE;

    public void updateDetails(String name, Modality modality, BigDecimal cost, Integer durationMinutes,
                              String description, String recommendations,
                              LocalDate startDate, LocalDate endDate, Comuna comuna) {
        this.name = name;
        this.modality = modality;
        this.cost = cost;
        this.durationMinutes = durationMinutes;
        this.description = description;
        this.recommendations = recommendations;
        this.startDate = startDate;
        this.endDate = endDate;
        this.comuna = comuna;
    }

    public void changeActive(boolean isActive) {
        this.isActive = isActive;
    }
}