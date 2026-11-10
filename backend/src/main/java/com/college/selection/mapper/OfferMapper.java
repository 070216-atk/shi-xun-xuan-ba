package com.college.selection.mapper;

import com.college.selection.entity.Offer;
import org.apache.ibatis.annotations.Mapper;
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

    /** 确认时先把这一行锁住，避免同一 offer 被并发操作 */
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
            SELECT * FROM offer
             WHERE student_id = #{studentId}
               AND round_no   = #{round}
               AND status     = 'CONFIRMED'
             LIMIT 1
            """)
    Offer findConfirmed(@Param("studentId") Long studentId, @Param("round") Integer round);
}
