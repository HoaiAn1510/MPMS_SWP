package com.example.manga_management.repository;

import com.example.manga_management.entity.LikeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RankingRepository extends JpaRepository<LikeResult, String> {

    // ===== BẢNG XẾP HẠNG =====
    // Xếp hạng theo TỔNG LƯỢT XEM (SUM viewCount trong LikeResult) giảm dần.
    // Tie-break: view bằng nhau -> like cao hơn xếp trên; like bằng nhau ->
    // dislike thấp hơn xếp trên.
    // Mỗi dòng trả về: [seriesId, seriesName, totalView, totalLike, totalDislike]

    @Query("SELECT s.id, s.seriesName, " +
           "COALESCE(SUM(v.viewCount), 0L), COALESCE(SUM(v.likeNumber), 0L), COALESCE(SUM(v.dislikeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id AND v.year = :year " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "HAVING COALESCE(SUM(v.viewCount), 0L) > 0 " +
           "ORDER BY COALESCE(SUM(v.viewCount), 0L) DESC, " +
           "COALESCE(SUM(v.likeNumber), 0L) DESC, COALESCE(SUM(v.dislikeNumber), 0L) ASC")
    List<Object[]> findRankingByYear(@Param("year") int year);

    @Query("SELECT s.id, s.seriesName, " +
           "COALESCE(SUM(v.viewCount), 0L), COALESCE(SUM(v.likeNumber), 0L), COALESCE(SUM(v.dislikeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id AND v.month = :month AND v.year = :year " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "HAVING COALESCE(SUM(v.viewCount), 0L) > 0 " +
           "ORDER BY COALESCE(SUM(v.viewCount), 0L) DESC, " +
           "COALESCE(SUM(v.likeNumber), 0L) DESC, COALESCE(SUM(v.dislikeNumber), 0L) ASC")
    List<Object[]> findRankingByMonthAndYear(@Param("month") int month,
                                             @Param("year") int year);

    @Query("SELECT s.id, s.seriesName, " +
           "COALESCE(SUM(v.viewCount), 0L), COALESCE(SUM(v.likeNumber), 0L), COALESCE(SUM(v.dislikeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id " +
           "AND v.year = :year AND v.month IN :months " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "HAVING COALESCE(SUM(v.viewCount), 0L) > 0 " +
           "ORDER BY COALESCE(SUM(v.viewCount), 0L) DESC, " +
           "COALESCE(SUM(v.likeNumber), 0L) DESC, COALESCE(SUM(v.dislikeNumber), 0L) ASC")
    List<Object[]> findRankingByQuarterAndYear(@Param("months") List<Integer> months,
                                               @Param("year") int year);

    // ===== 3 SERIES CUỐI BẢNG (view thấp nhất) — dùng để admin tạo vote dừng =====
    // Tie-break đảo lại: view bằng -> like thấp hơn tệ hơn (xếp trước);
    // like bằng -> dislike cao hơn tệ hơn.

    @Query("SELECT s.id, s.seriesName, " +
           "COALESCE(SUM(v.viewCount), 0L), COALESCE(SUM(v.likeNumber), 0L), COALESCE(SUM(v.dislikeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id AND v.year = :year " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "HAVING COALESCE(SUM(v.viewCount), 0L) > 0 " +
           "ORDER BY COALESCE(SUM(v.viewCount), 0L) ASC, " +
           "COALESCE(SUM(v.likeNumber), 0L) ASC, COALESCE(SUM(v.dislikeNumber), 0L) DESC")
    List<Object[]> findBottomByYear(@Param("year") int year);

    @Query("SELECT s.id, s.seriesName, " +
           "COALESCE(SUM(v.viewCount), 0L), COALESCE(SUM(v.likeNumber), 0L), COALESCE(SUM(v.dislikeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id AND v.month = :month AND v.year = :year " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "HAVING COALESCE(SUM(v.viewCount), 0L) > 0 " +
           "ORDER BY COALESCE(SUM(v.viewCount), 0L) ASC, " +
           "COALESCE(SUM(v.likeNumber), 0L) ASC, COALESCE(SUM(v.dislikeNumber), 0L) DESC")
    List<Object[]> findBottomByMonthAndYear(@Param("month") int month,
                                            @Param("year") int year);

    @Query("SELECT s.id, s.seriesName, " +
           "COALESCE(SUM(v.viewCount), 0L), COALESCE(SUM(v.likeNumber), 0L), COALESCE(SUM(v.dislikeNumber), 0L) " +
           "FROM Series s LEFT JOIN LikeResult v ON v.series.id = s.id " +
           "AND v.year = :year AND v.month IN :months " +
           "WHERE s.status <> 'stopped' " +
           "GROUP BY s.id, s.seriesName " +
           "HAVING COALESCE(SUM(v.viewCount), 0L) > 0 " +
           "ORDER BY COALESCE(SUM(v.viewCount), 0L) ASC, " +
           "COALESCE(SUM(v.likeNumber), 0L) ASC, COALESCE(SUM(v.dislikeNumber), 0L) DESC")
    List<Object[]> findBottomByQuarterAndYear(@Param("months") List<Integer> months,
                                              @Param("year") int year);

    @Query(value = "SELECT DISTINCT SeriesID, SeriesName, Status FROM series ORDER BY SeriesID ASC", nativeQuery = true)
    List<Object[]> findAllSeriesDistinct();

    // ===== Queries cho phiên vote thủ công (dùng SeriesVote) =====
    // Đếm theo đúng SessionID (không theo ngày) để tránh đếm nhầm phiếu của
    // phiên vote khác cho cùng series diễn ra cùng ngày (vd stop + defense).
    @Query("SELECT COUNT(sv) FROM SeriesVote sv WHERE sv.session.id = :sessionId")
    long countSeriesVoteBySession(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(sv) FROM SeriesVote sv " +
           "WHERE sv.session.id = :sessionId AND sv.board.id = :boardId")
    long countSeriesVoteByBoardAndSession(@Param("sessionId") String sessionId,
                                          @Param("boardId") String boardId);

    @Query("SELECT COUNT(sv) FROM SeriesVote sv " +
           "WHERE sv.session.id = :sessionId AND sv.vote = :voteChoice")
    long countSeriesVoteByChoiceAndSession(@Param("sessionId") String sessionId,
                                           @Param("voteChoice") String voteChoice);

    @Query(value = "SELECT Status FROM series WHERE SeriesID = :seriesId LIMIT 1", nativeQuery = true)
    String getSeriesStatus(@Param("seriesId") String seriesId);
}
