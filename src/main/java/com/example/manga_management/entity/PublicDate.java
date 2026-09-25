package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "publicDate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PublicDate {

    @Id
    @Column(name = "PublicID", length = 6)
    private String id;

    @ManyToOne
    @JoinColumn(name = "ChapterID", nullable = false)
    private Chapter chapter;

    @Column(name = "DatePublic", nullable = false)
    private LocalDate datePublic;
}
