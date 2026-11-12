package com.college.selection.mapper;

import com.college.selection.dto.ApplicantVO;
import com.college.selection.entity.InterviewInvite;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface InterviewInviteMapper {

    @Insert("""
            INSERT IGNORE INTO interview_invite (round_no, lab_id, mentor_id, student_id)
            VALUES (#{roundNo}, #{labId}, #{mentorId}, #{studentId})
            """)
    int insert(InterviewInvite invite);

    @Delete("""
            DELETE FROM interview_invite
             WHERE round_no = #{round} AND mentor_id = #{mentorId} AND student_id = #{studentId}
            """)
    int delete(@Param("round") Integer round,
               @Param("mentorId") Long mentorId,
               @Param("studentId") Long studentId);

    /** 本室面试名单：本室任一导师拉进来的人（去重），带评分状态 */
    @Select("""
            SELECT u.id            AS student_id,
                   u.code          AS code,
                   u.real_name     AS real_name,
                   u.class_name    AS class_name,
                   MIN(i.mentor_id) AS mentor_id,
                   GROUP_CONCAT(DISTINCT m.name ORDER BY m.sort_no SEPARATOR '、') AS mentor_name,
                   a.adjust_in_lab AS adjust_in_lab,
                   a.adjust_cross  AS adjust_cross,
                   MAX(s.score_attitude) AS score_attitude,
                   MAX(s.score_logic)    AS score_logic,
                   MAX(s.score_coding)   AS score_coding,
                   MAX(s.score_time)     AS score_time,
                   MAX(s.total)          AS score_total,
                   MAX(s.comment)        AS score_comment,
                   MAX(s.showcase)       AS showcase
              FROM interview_invite i
              JOIN sys_user u   ON u.id = i.student_id
              JOIN mentor m     ON m.id = i.mentor_id
              LEFT JOIN application a ON a.student_id = u.id AND a.round_no = #{round}
              LEFT JOIN interview_score s
                     ON s.student_id = u.id AND s.lab_id = #{labId} AND s.round_no = #{round}
             WHERE i.round_no = #{round} AND i.lab_id = #{labId}
             GROUP BY u.id, u.code, u.real_name, u.class_name, a.adjust_in_lab, a.adjust_cross
             ORDER BY (MAX(s.total) IS NULL), MAX(s.total) DESC, u.code
            """)
    List<ApplicantVO> findInterviewList(@Param("labId") Long labId, @Param("round") Integer round);

    /** 本室已入围的学生 id（用于给报名列表打标记、以及校验名单成员资格） */
    @Select("""
            SELECT DISTINCT student_id FROM interview_invite
             WHERE round_no = #{round} AND lab_id = #{labId}
            """)
    List<Long> findInvitedStudentIds(@Param("labId") Long labId, @Param("round") Integer round);

    /** 我（某导师）已邀请的学生 id */
    @Select("""
            SELECT student_id FROM interview_invite
             WHERE round_no = #{round} AND mentor_id = #{mentorId}
            """)
    List<Long> findInvitedByMentor(@Param("mentorId") Long mentorId, @Param("round") Integer round);
}
