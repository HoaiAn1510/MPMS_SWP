package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "board")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Board {

    @Id
    @Column(name = "BoardID", length = 6)
    private String id;

    @OneToOne
    @JoinColumn(name = "ID", nullable = false, unique = true)
    private User user;
}
