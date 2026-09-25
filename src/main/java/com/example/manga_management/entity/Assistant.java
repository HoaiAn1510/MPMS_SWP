package com.example.manga_management.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "assistant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Assistant {

    @Id
    @Column(name = "AssistantID", length = 6)
    private String id;

    @OneToOne
    @JoinColumn(name = "ID", nullable = false, unique = true)
    private User user;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "MangakaID", nullable = false)
    private Mangaka mangaka;

    @Column(name = "SalaryPerTask", nullable = false)
    private Integer salaryPerTask = 0;

    @Column(name = "Status", length = 10)
    private String status = "untask";
}
