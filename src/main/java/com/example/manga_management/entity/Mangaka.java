package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mangaka")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mangaka {

    @Id
    @Column(name = "MangakaID", length = 6)
    private String id;

    @OneToOne
    @JoinColumn(name = "ID", nullable = false, unique = true)
    private User user;

    @ManyToOne
    @JoinColumn(name = "EditorID", nullable = false)
    private TantoEditor editor;

    /** Lương cho mỗi chapter được Tantou duyệt, dùng để tính lương/thưởng theo tháng. */
    @Column(name = "SalaryPerChapter", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer salaryPerChapter = 0;
}
