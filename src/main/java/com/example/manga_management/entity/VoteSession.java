package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "voteSession")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VoteSession {

    @Id
    @Column(name = "SessionID", length = 8)
    private String id;

    @ManyToOne
    @JoinColumn(name = "SeriesID", nullable = false)
    private Series series;

    @ManyToOne
    @JoinColumn(name = "CreatedByBoardID", nullable = true)
    private Board createdBy;

    @Column(name = "VoteType", nullable = false, length = 10)
    private String voteType; // "stop" or "reward"

    @Column(name = "Status", nullable = false, length = 10)
    private String status; // "active" or "closed"

    @Column(name = "CreatedAt", nullable = false)
    private LocalDate createdAt;

    @Column(name = "AutoCreated", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean autoCreated = false;

    @Column(name = "Reason", length = 500)
    private String reason;

    @Column(name = "DefenseFilePath", length = 100)
    private String defenseFilePath;

    @Column(name = "DefenseNote", length = 500)
    private String defenseNote;

    /** Ngày phiên đóng (đủ số board vote) — dùng để tính "trong tháng này" và mốc 1 tuần chờ bảo vệ. */
    @Column(name = "ClosedAt")
    private LocalDate closedAt;

    /** Kết quả phiên khi đóng: true = đạt ngưỡng 60% đồng ý, false = không đạt. Null khi phiên chưa đóng. */
    @Column(name = "ResultPassed")
    private Boolean resultPassed;

    /**
     * Chỉ dùng cho voteType="reward" đã pass: số tiền thưởng đã tính và "chốt"
     * ngay lúc đóng phiên (10% x salaryPerChapter x số chapter published chưa
     * từng được thưởng). Lưu cố định ở đây để tháng sau không bị tính lại.
     */
    @Column(name = "RewardBonusAmount")
    private Integer rewardBonusAmount;
}
