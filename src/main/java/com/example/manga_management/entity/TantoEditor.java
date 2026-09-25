package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tantoreditor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TantoEditor {

    @Id
    @Column(name = "EditorID", length = 6)
    private String id;

    @OneToOne
    @JoinColumn(name = "ID", nullable = false, unique = true)
    private User user;
}
