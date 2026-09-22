package com.servas.domain.entity;

import com.servas.common.constant.DbTables;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = DbTables.COMUNAS)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Comuna {

    @Id
    @Column(nullable = false, updatable = false)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;
}