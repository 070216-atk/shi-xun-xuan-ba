package com.college.selection.mapper;

import com.college.selection.entity.Offer;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OfferMapper {

    @Select("""
            SELECT o.*, l.code AS lab_code, l.name AS lab_name, m.name AS mentor_name
              FROM offer o
              JOIN lab l    ON l.id = o.lab_id
              JOIN mentor m ON m.id = o.mentor_id
             WHERE o.student_id = #{studentId} AND o.round_no = #{round}
             ORDER BY FIELD(o.type, 'ADMITTED', 'WAITLIST', 'ADJUST_CANDIDATE'), o.rank_no, o.id
            """)
    List<Offer> findByStudent(@Param("studentId") Long studentId, @Param("round") Integer round);

    /** 导师端：本导师本轮的名单，带学生姓名和面试分 */
    @Select("""
            SELECT o.*, l.code AS lab_code, l.name AS lab_name, m.name AS mentor_name,
                   u.real_name AS student_name, u.code AS student_code,
                   (SELECT s.total FROM interview_score s
                     WHERE s.student_id = o.student_id AND s.lab_id = o.lab_id AND s.round_no = o.round_no) AS score_total
              FROM offer o
              JOIN lab l     ON l.id = o.lab_id
              JOIN mentor m  ON m.id = o.mentor_id
              JOIN sys_user u ON u.id = o.student_id
             WHERE o.mentor_id = #{mentorId} AND o.round_no = #{round}
             ORDER BY FIELD(o.type, 'ADMITTED', 'WAITLIST', 'ADJUST_CANDIDATE'), o.rank_no, o.id
            """)
    List<Offer> findByMentor(@Param("mentorId") Long mentorId, @Param("round") Integer round);

    /** 确认时先把这一行锁住 */
    @Select("SELECT * FROM offer WHERE id = #{id} FOR UPDATE")
    Offer lockById(@Param("id") Long id);

    @Update("UPDATE offer SET status = #{status}, confirmed_at = #{confirmedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("confirmedAt") LocalDateTime confirmedAt);

    /** 确认一个去向之后，同轮其余还在 ISSUED 的意向全部置为放弃 */
    @Update("""
            UPDATE offer
               SET status = 'DECLINED'
             WHERE student_id = #{studentId}
               AND round_no   = #{round}
               AND status     = 'ISSUED'
               AND id        <> #{keepId}
            """)
    int declineOthers(@Param("studentId") Long studentId,
                      @Param("round") Integer round,
                      @Param("keepId") Long keepId);

    /** 锁住导师这一行再数已确认人数，防止并发超录 */
    @Select("SELECT quota FROM mentor WHERE id = #{mentorId} FOR UPDATE")
    Integer lockMentorQuota(@Param("mentorId") Long mentorId);

    @Select("""
            SELECT COUNT(*) FROM offer
             WHERE mentor_id = #{mentorId}
               AND round_no  = #{round}
               AND type      = 'ADMITTED'
               AND status    = 'CONFIRMED'
            """)
    int countConfirmed(@Param("mentorId") Long mentorId, @Param("round") Integer round);

    @Select("""
            SELECT o.*, l.code AS lab_code, l.name AS lab_name, m.name AS mentor_name
              FROM offer o
              JOIN lab l    ON l.id = o.lab_id
              JOIN mentor m ON m.id = o.mentor_id
             WHERE o.student_id = #{studentId} AND o.round_no = #{round} AND o.status = 'CONFIRMED'
             LIMIT 1
            """)
    Offer findConfirmed(@Param("studentId") Long studentId, @Param("round") Integer round);

    // ---------------- 导师提交名单时用 ----------------

    @Delete("DELETE FROM offer WHERE mentor_id = #{mentorId} AND round_no = #{round}")
    int deleteByMentor(@Param("mentorId") Long mentorId, @Param("round") Integer round);

    /** 统筹调剂时先清掉该学生投给这个导师的旧意向，避免撞唯一键 */
    @Delete("DELETE FROM offer WHERE student_id = #{studentId} AND mentor_id = #{mentorId} AND round_no = #{round}")
    int deleteByStudentMentor(@Param("studentId") Long studentId,
                              @Param("mentorId") Long mentorId,
                              @Param("round") Integer round);

    @Insert("""
            INSERT INTO offer (student_id, lab_id, mentor_id, round_no, type, rank_no, status, issued_at, confirm_deadline)
            VALUES (#{studentId}, #{labId}, #{mentorId}, #{roundNo}, #{type}, #{rankNo}, #{status}, #{issuedAt}, #{confirmDeadline})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Offer offer);

    /** 该学生本轮还有几条"已下发但未确认"的拟录取 */
    @Select("""
            SELECT COUNT(*) FROM offer
             WHERE student_id = #{studentId} AND round_no = #{round}
               AND type = 'ADMITTED' AND status = 'ISSUED'
            """)
    int countAdmittedIssued(@Param("studentId") Long studentId, @Param("round") Integer round);

    /** 该学生本轮是否已经确认了某个去向（可能是别的导师） */
    @Select("""
            SELECT COUNT(*) FROM offer
             WHERE student_id = #{studentId} AND round_no = #{round} AND status = 'CONFIRMED'
            """)
    int countAnyConfirmed(@Param("studentId") Long studentId, @Param("round") Integer round);

    /** 该导师本轮已确认的学生（用于拦住"把已确认学生从名单里删掉"） */
    @Select("""
            SELECT student_id FROM offer
             WHERE mentor_id = #{mentorId} AND round_no = #{round} AND status = 'CONFIRMED'
            """)
    List<Long> findConfirmedStudentIds(@Param("mentorId") Long mentorId, @Param("round") Integer round);

    /** 撤销录取后，把递补第一名提升为拟录取 */
    @Update("""
            UPDATE offer
               SET type = 'ADMITTED', rank_no = NULL, confirm_deadline = #{deadline}
             WHERE id = #{id} AND status = 'ISSUED'
            """)
    int promoteToAdmitted(@Param("id") Long id, @Param("deadline") LocalDateTime deadline);

    /** 结算时刷新 deadline 用 */
    @Update("""
            UPDATE offer
               SET confirm_deadline = #{deadline}
             WHERE round_no = #{round} AND type = 'ADMITTED' AND status = 'ISSUED'
            """)
    int refreshIssuedDeadline(@Param("round") Integer round, @Param("deadline") LocalDateTime deadline);

    /** 自动确认（只有 1 个拟录取意向时） */
    @Update("UPDATE offer SET status = 'CONFIRMED', confirmed_at = NOW() WHERE id = #{id} AND status = 'ISSUED'")
    int autoConfirm(@Param("id") Long id);

    /** 结算时用：把某个学生本轮唯一的那条拟录取意向直接确认 */
    @Update("""
            UPDATE offer
               SET status = 'CONFIRMED', confirmed_at = NOW()
             WHERE student_id = #{studentId} AND round_no = #{round}
               AND type = 'ADMITTED' AND status = 'ISSUED'
            """)
    int autoConfirmByStudent(@Param("studentId") Long studentId, @Param("round") Integer round);
}
