package com.tregy.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "theme")
@Getter
@Setter
@NoArgsConstructor
public class Theme {

    @Id
    @Column(length = 30, updatable = false)
    private String code;

    @Column(name = "name_en", nullable = false, length = 60)
    private String nameEn;

    @Column(name = "name_it", nullable = false, length = 60)
    private String nameIt;

    @Column(nullable = false)
    private boolean selectable;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
