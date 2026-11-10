package com.college.selection.mapper;

import com.college.selection.dto.AdjustPoolVO;
import com.college.selection.dto.QuotaVO;
import com.college.selection.entity.Publicity;
import com.college.selection.entity.Stage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminMapper {

    // ---------------- 名额总览 ----------------

    @Select("""
            SELECT l.id AS lab_id, l.code, l.name,
                   (SELECT COALESCE(SUM(m.quota), 0) FROM mentor m WHERE m.lab_id = l.id) AS quota,
                   (SELECT COUNT(DISTINCT a.student_id)
                      FROM application a
                      JOIN application_item ai ON ai.application_id = a.id
                     WHERE ai.lab_id = l.id AND a.round_no = #{round} AND a.status = 'SUBMITTED') AS applied,
                   (SELECT COUNT(*) FROM offer o
                     WHERE o.lab_id = l.id AND o.round_no = #{round}
                       AND o.type = 'ADMITTED' AND o.status = 'CONFIRMED') AS confirmed,
                   (SELECT COUNT(*) FROM offer o
                     WHERE o.lab_id = l.id AND o.round_no = #{round}
                       AND o.type = 'ADMITTED' AND o.status = 'ISSUED') AS issued
              FROM lab l
             ORDER BY l.code
            """)
    List<QuotaVO> quotas(@Param("round") Integer round);

    // ---------------- 调剂池 ----------------

    @Select("""
            SELECT m.id AS mentor_id, m.name AS mentor_name,
                   l.id AS lab_id, l.code AS lab_code, l.name AS lab_name,
                   m.quota,
                   (SELECT COUNT(*) FROM offer o
                     WHERE o.mentor_id = m.id AND o.round_no = #{round}
                       AND o.type = 'ADMITTED' AND o.status = 'CONFIRMED') AS confirmed
              FROM mentor m
              JOIN lab l ON l.id = m.lab_id
             WHERE m.quota > 0
             ORDER BY l.code, m.sort_no
            """)
    List<AdjustPoolVO.Vacancy> vacancies(@Param("round") Integer round);

    @Select("""
            SELECT u.id AS student_id, u.code, u.real_name, u.class_name,
                   a.adjust_in_lab, a.adjust_cross,
                   (SELECT o.type FROM offer o
                     WHERE o.student_id = u.id AND o.round_no = #{round}
                     ORDER BY FIELD(o.type, 'ADMITTED', 'WAITLIST', 'ADJUST_CANDIDATE') LIMIT 1) AS offer_type,
                   (SELECT o.status FROM offer o
                     WHERE o.student_id = u.id AND o.round_no = #{round}
                     ORDER BY FIELD(o.type, 'ADMITTED', 'WAITLIST', 'ADJUST_CANDIDATE') LIMIT 1) AS offer_status,
                   (SELECT o.rank_no FROM offer o
                     WHERE o.student_id = u.id AND o.round_no = #{round}
                     ORDER BY FIELD(o.type, 'ADMITTED', 'WAITLIST', 'ADJUST_CANDIDATE') LIMIT 1) AS rank_no
              FROM sys_user u
              JOIN application a ON a.student_id = u.id
                                 AND a.round_no = #{round} AND a.status = 'SUBMITTED'
             WHERE u.role = 'STUDENT'
               AND NOT EXISTS (SELECT 1 FROM offer o2
                                WHERE o2.student_id = u.id AND o2.round_no = #{round}
                                  AND o2.status = 'CONFIRMED')
             ORDER BY u.code
            """)
    List<AdjustPoolVO.Candidate> candidates(@Param("round") Integer round);

    /** 还答应服从室内调剂、但没着落的人数（第一优先级没做完就不能进第二级） */
    @Select("""
            SELECT COUNT(*) FROM application a
             WHERE a.round_no = #{round} AND a.status = 'SUBMITTED' AND a.adjust_in_lab = 1
               AND NOT EXISTS (SELECT 1 FROM offer o
                                WHERE o.student_id = a.student_id AND o.round_no = #{round}
                                  AND o.status = 'CONFIRMED')
            """)
    int countPendingInternal(@Param("round") Integer round);

    /** 还答应服从跨室调剂、但没着落的人数 */
    @Select("""
            SELECT COUNT(*) FROM application a
             WHERE a.round_no = #{round} AND a.status = 'SUBMITTED' AND a.adjust_cross = 1
               AND NOT EXISTS (SELECT 1 FROM offer o
                                WHERE o.student_id = a.student_id AND o.round_no = #{round}
                                  AND o.status = 'CONFIRMED')
            """)
    int countPendingCross(@Param("round") Integer round);

    // ---------------- 阶段 ----------------

    @Select("SELECT * FROM stage WHERE round_no = #{round} ORDER BY stage_no")
    List<Stage> allStages(@Param("round") Integer round);

    @Update("UPDATE stage SET start_at = #{startAt}, end_at = #{endAt} WHERE id = #{id}")
    int updateStage(@Param("id") Long id,
                    @Param("startAt") LocalDateTime startAt,
                    @Param("endAt") LocalDateTime endAt);

    // ---------------- 公示 ----------------

    @Select("SELECT * FROM publicity WHERE round_no = #{round} ORDER BY id DESC LIMIT 1")
    Publicity latestPublicity(@Param("round") Integer round);

    @Insert("""
            INSERT INTO publicity (round_no, status, start_at, end_at, objection_count, published_by, published_at)
            VALUES (#{roundNo}, #{status}, #{startAt}, #{endAt}, #{objectionCount}, #{publishedBy}, #{publishedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPublicity(Publicity p);

    @Update("UPDATE publicity SET status = #{status}, end_at = #{endAt} WHERE id = #{id}")
    int updatePublicity(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("endAt") LocalDateTime endAt);

    // ---------------- 看板 ----------------

    @Select("SELECT COUNT(DISTINCT student_id) FROM application WHERE round_no = #{round} AND status = 'SUBMITTED'")
    int countAppliedStudents(@Param("round") Integer round);

    @Select("SELECT COUNT(DISTINCT student_id) FROM interview_score WHERE round_no = #{round}")
    int countInterviewed(@Param("round") Integer round);

    @Select("SELECT COUNT(*) FROM offer WHERE round_no = #{round} AND type = 'ADMITTED' AND status = 'ISSUED'")
    int countIssued(@Param("round") Integer round);

    @Select("SELECT COUNT(*) FROM offer WHERE round_no = #{round} AND status = 'CONFIRMED'")
    int countConfirmedOffers(@Param("round") Integer round);

    @Select("SELECT COUNT(*) FROM offer WHERE round_no = #{round} AND type = 'WAITLIST'")
    int countWaitlist(@Param("round") Integer round);

    @Select("SELECT COUNT(*) FROM offer WHERE round_no = #{round} AND type = 'ADJUST_CANDIDATE'")
    int countAdjustCandidates(@Param("round") Integer round);
}
