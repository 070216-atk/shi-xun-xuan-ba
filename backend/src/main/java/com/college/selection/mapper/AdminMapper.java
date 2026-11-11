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

    /** 切换阶段时，先把本轮所有阶段压到过去（相当于全部关闭） */
    @Update("""
            UPDATE stage
               SET start_at = DATE_SUB(#{now}, INTERVAL 2 DAY),
                   end_at   = DATE_SUB(#{now}, INTERVAL 1 DAY)
             WHERE round_no = #{round}
            """)
    int closeAllStages(@Param("round") Integer round, @Param("now") LocalDateTime now);

    /** 再单独打开目标阶段 */
    @Update("""
            UPDATE stage
               SET start_at = #{startAt}, end_at = #{endAt}
             WHERE round_no = #{round} AND stage_no = #{stageNo}
            """)
    int openStage(@Param("round") Integer round,
                  @Param("stageNo") Integer stageNo,
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

    /** 结算时用：本轮还有"已下发未确认"拟录取意向的学生 */
    @Select("""
            SELECT DISTINCT student_id FROM offer
             WHERE round_no = #{round} AND type = 'ADMITTED' AND status = 'ISSUED'
            """)
    java.util.List<Long> studentsWithIssuedAdmitted(@Param("round") Integer round);

    // ---------------- 补录轮次 ----------------

    @Select("SELECT status FROM selection_round WHERE round_no = #{round}")
    String roundStatus(@Param("round") Integer round);

    @Update("UPDATE selection_round SET status = 'CLOSED', closed_at = NOW() WHERE round_no = #{round}")
    int closeRound(@Param("round") Integer round);

    @Update("UPDATE selection_round SET status = 'OPEN', opened_at = NOW() WHERE round_no = #{round}")
    int openRound(@Param("round") Integer round);

    @Select("SELECT COUNT(*) FROM stage WHERE round_no = #{round}")
    int countStages(@Param("round") Integer round);

    /** 新一轮的阶段先照抄上一轮，再由管理员在 /admin/stages 里逐个改时间 */
    @Insert("""
            INSERT INTO stage (round_no, stage_no, name, start_at, end_at, description, is_open)
            SELECT #{round}, stage_no, name, start_at, end_at, description, is_open
              FROM stage WHERE round_no = #{prevRound}
            """)
    int copyStages(@Param("round") Integer round, @Param("prevRound") Integer prevRound);

    // ---------------- 公示异议 ----------------

    @Update("UPDATE publicity SET objection_count = objection_count + 1 WHERE id = #{id}")
    int addObjection(@Param("id") Long id);

    @Update("UPDATE publicity SET objection_count = 0 WHERE id = #{id}")
    int clearObjections(@Param("id") Long id);

    // ---------------- 最终名单（公示用） ----------------

    @Select("""
            SELECT u.code AS code, u.real_name AS realName, u.class_name AS className,
                   l.id AS labId, l.code AS labCode, l.name AS labName, m.name AS mentorName
              FROM offer o
              JOIN sys_user u ON u.id = o.student_id
              JOIN lab l      ON l.id = o.lab_id
              JOIN mentor m   ON m.id = o.mentor_id
             WHERE o.round_no = #{round} AND o.status = 'CONFIRMED'
             ORDER BY l.code, u.code
            """)
    java.util.List<java.util.Map<String, Object>> confirmedResults(@Param("round") Integer round);

    // ---------------- 学生名单 ----------------

    @Select("""
            SELECT id AS studentId, code AS code, real_name AS realName, class_name AS className
              FROM sys_user
             WHERE role = 'STUDENT' AND status = 1
             ORDER BY code
            """)
    java.util.List<java.util.Map<String, Object>> allStudents();
}
