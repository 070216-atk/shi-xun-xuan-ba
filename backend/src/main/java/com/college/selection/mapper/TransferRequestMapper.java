package com.college.selection.mapper;

import com.college.selection.entity.TransferRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TransferRequestMapper {

    @Insert("""
            INSERT INTO transfer_request
                (student_id, round_no, from_offer_id, from_mentor_id, from_lab_id,
                 target_lab_id, target_mentor_id, reason, status)
            VALUES
                (#{studentId}, #{roundNo}, #{fromOfferId}, #{fromMentorId}, #{fromLabId},
                 #{targetLabId}, #{targetMentorId}, #{reason}, 'PENDING')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TransferRequest r);

    @Select("SELECT * FROM transfer_request WHERE id = #{id} FOR UPDATE")
    TransferRequest lockById(@Param("id") Long id);

    @Select("""
            SELECT t.*,
                   u.real_name  AS student_name, u.code AS student_code,
                   fl.code AS from_lab_code,   fl.name AS from_lab_name,  fm.name AS from_mentor_name,
                   tl.code AS target_lab_code, tl.name AS target_lab_name, tm.name AS target_mentor_name
              FROM transfer_request t
              JOIN sys_user u  ON u.id  = t.student_id
              JOIN lab fl      ON fl.id = t.from_lab_id
              JOIN mentor fm   ON fm.id = t.from_mentor_id
              JOIN lab tl      ON tl.id = t.target_lab_id
              JOIN mentor tm   ON tm.id = t.target_mentor_id
             WHERE t.id = #{id}
            """)
    TransferRequest findById(@Param("id") Long id);

    /** 学生本轮还有没有正在处理的申请（一人同时只能有一个） */
    @Select("""
            SELECT t.*,
                   u.real_name  AS student_name, u.code AS student_code,
                   fl.code AS from_lab_code,   fl.name AS from_lab_name,  fm.name AS from_mentor_name,
                   tl.code AS target_lab_code, tl.name AS target_lab_name, tm.name AS target_mentor_name
              FROM transfer_request t
              JOIN sys_user u  ON u.id  = t.student_id
              JOIN lab fl      ON fl.id = t.from_lab_id
              JOIN mentor fm   ON fm.id = t.from_mentor_id
              JOIN lab tl      ON tl.id = t.target_lab_id
              JOIN mentor tm   ON tm.id = t.target_mentor_id
             WHERE t.student_id = #{studentId} AND t.round_no = #{round} AND t.status = 'PENDING'
             ORDER BY t.id DESC
             LIMIT 1
            """)
    TransferRequest findPendingByStudent(@Param("studentId") Long studentId,
                                         @Param("round") Integer round);

    /** 学生自己的全部申请（含已处理的历史） */
    @Select("""
            SELECT t.*,
                   u.real_name  AS student_name, u.code AS student_code,
                   fl.code AS from_lab_code,   fl.name AS from_lab_name,  fm.name AS from_mentor_name,
                   tl.code AS target_lab_code, tl.name AS target_lab_name, tm.name AS target_mentor_name
              FROM transfer_request t
              JOIN sys_user u  ON u.id  = t.student_id
              JOIN lab fl      ON fl.id = t.from_lab_id
              JOIN mentor fm   ON fm.id = t.from_mentor_id
              JOIN lab tl      ON tl.id = t.target_lab_id
              JOIN mentor tm   ON tm.id = t.target_mentor_id
             WHERE t.student_id = #{studentId} AND t.round_no = #{round}
             ORDER BY t.id DESC
            """)
    List<TransferRequest> findByStudent(@Param("studentId") Long studentId,
                                        @Param("round") Integer round);

    /** 管理端：本轮所有转组申请（待接收的排前面，其余按时间倒序） */
    @Select("""
            SELECT t.*,
                   u.real_name  AS student_name, u.code AS student_code,
                   fl.code AS from_lab_code,   fl.name AS from_lab_name,  fm.name AS from_mentor_name,
                   tl.code AS target_lab_code, tl.name AS target_lab_name, tm.name AS target_mentor_name
              FROM transfer_request t
              JOIN sys_user u  ON u.id  = t.student_id
              JOIN lab fl      ON fl.id = t.from_lab_id
              JOIN mentor fm   ON fm.id = t.from_mentor_id
              JOIN lab tl      ON tl.id = t.target_lab_id
              JOIN mentor tm   ON tm.id = t.target_mentor_id
             WHERE t.round_no = #{round}
             ORDER BY (t.status = 'PENDING') DESC, t.id DESC
            """)
    List<TransferRequest> findByRound(@Param("round") Integer round);

    /** 导师端：指定转给我的、还没处理的申请 */    @Select("""
            SELECT t.*,
                   u.real_name  AS student_name, u.code AS student_code,
                   fl.code AS from_lab_code,   fl.name AS from_lab_name,  fm.name AS from_mentor_name,
                   tl.code AS target_lab_code, tl.name AS target_lab_name, tm.name AS target_mentor_name
              FROM transfer_request t
              JOIN sys_user u  ON u.id  = t.student_id
              JOIN lab fl      ON fl.id = t.from_lab_id
              JOIN mentor fm   ON fm.id = t.from_mentor_id
              JOIN lab tl      ON tl.id = t.target_lab_id
              JOIN mentor tm   ON tm.id = t.target_mentor_id
             WHERE t.target_mentor_id = #{mentorId} AND t.round_no = #{round} AND t.status = 'PENDING'
             ORDER BY t.id
            """)
    List<TransferRequest> findPendingForMentor(@Param("mentorId") Long mentorId,
                                               @Param("round") Integer round);

    @Update("""
            UPDATE transfer_request
               SET status = #{status},
                   interview_note = #{interviewNote},
                   reject_reason  = #{rejectReason},
                   handled_by     = #{handledBy},
                   handled_at     = #{handledAt}
             WHERE id = #{id}
            """)
    int updateHandled(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("interviewNote") String interviewNote,
                      @Param("rejectReason") String rejectReason,
                      @Param("handledBy") Long handledBy,
                      @Param("handledAt") LocalDateTime handledAt);
}