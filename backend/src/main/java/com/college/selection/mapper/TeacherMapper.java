package com.college.selection.mapper;

import com.college.selection.dto.ApplicantVO;
import com.college.selection.dto.ScoreReq;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeacherMapper {

    /**
     * 本实训室的报名学生。
     * 不管名额满不满，只要报了本室就必须出现在这里（PRD R4：全员面试）。
     */
    @Select("""
            SELECT u.id            AS student_id,
                   u.code          AS code,
                   u.real_name     AS real_name,
                   u.class_name    AS class_name,
                   ai.mentor_id    AS mentor_id,
                   m.name          AS mentor_name,
                   a.adjust_in_lab AS adjust_in_lab,
                   a.adjust_cross  AS adjust_cross,
                   s.score_attitude AS score_attitude,
                   s.score_logic    AS score_logic,
                   s.score_coding   AS score_coding,
                   s.score_time     AS score_time,
                   s.total          AS score_total,
                   s.comment        AS score_comment
              FROM application a
              JOIN application_item ai ON ai.application_id = a.id
              JOIN sys_user u          ON u.id = a.student_id
              JOIN mentor m            ON m.id = ai.mentor_id
              LEFT JOIN interview_score s
                     ON s.student_id = u.id AND s.lab_id = #{labId} AND s.round_no = #{round}
             WHERE a.round_no = #{round}
               AND a.status   = 'SUBMITTED'
               AND ai.lab_id  = #{labId}
             ORDER BY m.sort_no, u.code
            """)
    List<ApplicantVO> findApplicants(@Param("labId") Long labId, @Param("round") Integer round);

    /** 该学生是否报了本室（防止给别的室的学生打分） */
    @Select("""
            SELECT COUNT(*)
              FROM application a
              JOIN application_item ai ON ai.application_id = a.id
             WHERE a.student_id = #{studentId}
               AND a.round_no   = #{round}
               AND a.status     = 'SUBMITTED'
               AND ai.lab_id    = #{labId}
            """)
    int countAppliedToLab(@Param("studentId") Long studentId,
                          @Param("labId") Long labId,
                          @Param("round") Integer round);

    /** 有则更新、无则插入（依赖 interview_score 上 (student_id, lab_id, round_no) 的唯一键） */
    @Insert("""
            INSERT INTO interview_score
                (student_id, lab_id, round_no, mentor_id,
                 score_attitude, score_logic, score_coding, score_time, comment, scored_by)
            VALUES
                (#{studentId}, #{labId}, #{round}, #{mentorId},
                 #{req.attitude}, #{req.logic}, #{req.coding}, #{req.time}, #{req.comment}, #{scoredBy})
            ON DUPLICATE KEY UPDATE
                score_attitude = VALUES(score_attitude),
                score_logic    = VALUES(score_logic),
                score_coding   = VALUES(score_coding),
                score_time     = VALUES(score_time),
                comment        = VALUES(comment),
                scored_by      = VALUES(scored_by),
                scored_at      = NOW()
            """)
    int upsertScore(@Param("studentId") Long studentId,
                    @Param("labId") Long labId,
                    @Param("round") Integer round,
                    @Param("mentorId") Long mentorId,
                    @Param("req") ScoreReq req,
                    @Param("scoredBy") Long scoredBy);
}
