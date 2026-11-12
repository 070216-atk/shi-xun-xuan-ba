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
                   tm.name AS target_mentor_name,
                   u.real_name AS student_name, u.code AS student_code,
                   (SELECT s.total FROM interview_score s
                     WHERE s.student_id = o.student_id AND s.lab_id = o.lab_id AND s.round_no = o.round_no) AS score_total
              FROM offer o
              JOIN lab l     ON l.id = o.lab_id
              JOIN mentor m  ON m.id = o.mentor_id
              LEFT JOIN mentor tm ON tm.id = o.target_mentor_id
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

    /**
     * 该学生"截至某一轮（含）"是否已经确认过某个去向。
     * 录取是终局状态：前一轮确认了，后面几轮就不能再填报、也不能再被下发意向。
     */
    @Select("""
            SELECT o.*, l.code AS lab_code, l.name AS lab_name, m.name AS mentor_name
              FROM offer o
              JOIN lab l    ON l.id = o.lab_id
              JOIN mentor m ON m.id = o.mentor_id
             WHERE o.student_id = #{studentId} AND o.round_no <= #{round} AND o.status = 'CONFIRMED'
             ORDER BY o.round_no
             LIMIT 1
            """)
    Offer findConfirmedUpTo(@Param("studentId") Long studentId, @Param("round") Integer round);

    // ---------------- 导师提交名单时用 ----------------

    @Delete("DELETE FROM offer WHERE mentor_id = #{mentorId} AND round_no = #{round}")
    int deleteByMentor(@Param("mentorId") Long mentorId, @Param("round") Integer round);

    /** 统筹调剂时先清掉该学生投给这个导师的旧意向，避免撞唯一键 */
    @Delete("DELETE FROM offer WHERE student_id = #{studentId} AND mentor_id = #{mentorId} AND round_no = #{round}")
    int deleteByStudentMentor(@Param("studentId") Long studentId,
                              @Param("mentorId") Long mentorId,
                              @Param("round") Integer round);

    @Insert("""
            INSERT INTO offer (student_id, lab_id, mentor_id, target_mentor_id, round_no, type, rank_no, status, issued_at, confirm_deadline)
            VALUES (#{studentId}, #{labId}, #{mentorId}, #{targetMentorId}, #{roundNo}, #{type}, #{rankNo}, #{status}, #{issuedAt}, #{confirmDeadline})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Offer offer);

    /** 接收方导师：别人指定调剂给我的学生 */
    @Select("""
            SELECT o.*, u.real_name AS student_name, u.code AS student_code, u.class_name,
                   m.name AS mentor_name, l.code AS lab_code, l.name AS lab_name,
                   tm.name AS target_mentor_name,
                   (SELECT s.total FROM interview_score s
                     WHERE s.student_id = o.student_id AND s.lab_id = o.lab_id AND s.round_no = o.round_no) AS score_total
              FROM offer o
              JOIN sys_user u ON u.id = o.student_id
              JOIN mentor m   ON m.id = o.mentor_id
              JOIN lab l      ON l.id = o.lab_id
              LEFT JOIN mentor tm ON tm.id = o.target_mentor_id
             WHERE o.round_no = #{round} AND o.type = 'ADJUST_CANDIDATE'
               AND o.status = 'ISSUED' AND o.target_mentor_id = #{mentorId}
             ORDER BY o.rank_no, o.id
            """)
    List<Offer> findAdjustInbox(@Param("mentorId") Long mentorId, @Param("round") Integer round);

    /** 我已占用的拟录取名额（已下发 + 已确认都算） */
    @Select("""
            SELECT COUNT(*) FROM offer
             WHERE mentor_id = #{mentorId} AND round_no = #{round}
               AND type = 'ADMITTED' AND status IN ('ISSUED', 'CONFIRMED')
            """)
    int countAdmittedByMentor(@Param("mentorId") Long mentorId, @Param("round") Integer round);

    /** 我当前拟录取的最大顺位，用于把接收的调剂学生排在后面 */
    @Select("""
            SELECT COALESCE(MAX(rank_no), 0) FROM offer
             WHERE mentor_id = #{mentorId} AND round_no = #{round} AND type = 'ADMITTED'
            """)
    int maxAdmittedRank(@Param("mentorId") Long mentorId, @Param("round") Integer round);

    /** 选课式：确认新的去向时，把自己之前确认的那条"释放"回待确认（不判放弃，还能反悔） */
    @Update("""
            UPDATE offer
               SET status = 'ISSUED', confirmed_at = NULL
             WHERE student_id = #{studentId} AND round_no = #{round}
               AND status = 'CONFIRMED' AND id <> #{keepId}
            """)
    int releaseOthers(@Param("studentId") Long studentId,
                      @Param("round") Integer round,
                      @Param("keepId") Long keepId);

    /** 查我本轮已确认但当前又被释放的那几条（用来给对应导师递补） */
    @Select("""
            SELECT * FROM offer
             WHERE student_id = #{studentId} AND round_no = #{round}
               AND status = 'CONFIRMED' AND id <> #{keepId}
            """)
    List<Offer> findOtherConfirmed(@Param("studentId") Long studentId,
                                   @Param("round") Integer round,
                                   @Param("keepId") Long keepId);

    /** 某导师的递补队列（按顺位） */
    @Select("""
            SELECT * FROM offer
             WHERE mentor_id = #{mentorId} AND round_no = #{round}
               AND type = 'WAITLIST' AND status = 'ISSUED'
             ORDER BY COALESCE(rank_no, 9999), id
            """)
    List<Offer> findWaitlist(@Param("mentorId") Long mentorId, @Param("round") Integer round);

    /** 撤销已确认的录取（学生反悔） */
    @Update("""
            UPDATE offer SET status = 'ISSUED', confirmed_at = NULL
             WHERE id = #{id} AND status = 'CONFIRMED'
            """)
    int releaseConfirmed(@Param("id") Long id);

    /** 某学生本轮"待确认"的拟录取，按顺位排好（收口时挑第一顺位） */
    @Select("""
            SELECT * FROM offer
             WHERE student_id = #{studentId} AND round_no = #{round}
               AND type = 'ADMITTED' AND status = 'ISSUED'
             ORDER BY COALESCE(rank_no, 9999), id
            """)
    List<Offer> findIssuedAdmitted(@Param("studentId") Long studentId, @Param("round") Integer round);

    /** 收口时把该学生其他还没确认的拟录取作废（避免留下悬空的待确认） */
    @Update("""
            UPDATE offer SET status = 'EXPIRED'
             WHERE student_id = #{studentId} AND round_no = #{round}
               AND type = 'ADMITTED' AND status = 'ISSUED' AND id <> #{keepId}
            """)
    int expireOtherIssued(@Param("studentId") Long studentId,
                          @Param("round") Integer round,
                          @Param("keepId") Long keepId);

    /** 写备注（学院统筹安置的说明写在这里，学生页面能看到） */
    @Update("UPDATE offer SET remark = #{remark} WHERE id = #{id}")
    int setRemark(@Param("id") Long id, @Param("remark") String remark);

    /** 管理员改派时，把该学生原来的确认记录作废（转走） */
    @Update("""
            UPDATE offer SET status = 'EXPIRED'
             WHERE student_id = #{studentId} AND round_no = #{round}
               AND status = 'CONFIRMED' AND id <> #{keepId}
            """)
    int expireOtherConfirmed(@Param("studentId") Long studentId,
                             @Param("round") Integer round,
                             @Param("keepId") Long keepId);

    /** 收口时把该学生其他所有未确认的意向（含递补、调剂候选）一并作废 */
    @Update("""
            UPDATE offer SET status = 'EXPIRED'
             WHERE student_id = #{studentId} AND round_no = #{round}
               AND status = 'ISSUED' AND id <> #{keepId}
            """)
    int expireAllOther(@Param("studentId") Long studentId,
                       @Param("round") Integer round,
                       @Param("keepId") Long keepId);

    /** 该学生在本轮是否已经有这个导师的有效意向（拟录取/递补/调剂候选，未作废的） */
    @Select("""
            SELECT COUNT(*) FROM offer
             WHERE student_id = #{studentId} AND mentor_id = #{mentorId} AND round_no = #{round}
               AND status NOT IN ('VOID', 'DECLINED')
            """)
    int countActiveOffer(@Param("studentId") Long studentId,
                         @Param("mentorId") Long mentorId,
                         @Param("round") Integer round);

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
               SET type = 'ADMITTED', rank_no = #{rank}, confirm_deadline = #{deadline}
             WHERE id = #{id} AND status = 'ISSUED'
            """)
    int promoteToAdmitted(@Param("id") Long id, @Param("rank") Integer rank, @Param("deadline") LocalDateTime deadline);

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
