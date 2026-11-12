package com.college.selection.mapper;

import com.college.selection.entity.Application;
import com.college.selection.entity.ApplicationItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ApplicationMapper {

    @Select("SELECT * FROM application WHERE student_id = #{studentId} AND round_no = #{round}")
    Application find(@Param("studentId") Long studentId, @Param("round") Integer round);

    @Select("""
            SELECT ai.*, l.code AS lab_code, l.name AS lab_name, m.name AS mentor_name
              FROM application_item ai
              JOIN lab l    ON l.id = ai.lab_id
              JOIN mentor m ON m.id = ai.mentor_id
             WHERE ai.application_id = #{appId}
             ORDER BY ai.sort_no
            """)
    List<ApplicationItem> findItems(@Param("appId") Long appId);

    /** 该学生本轮一共填了几个平行意向（补录阶段判断"能不能就地自动确认"用） */
    @Select("""
            SELECT COUNT(*) FROM application_item ai
              JOIN application a ON a.id = ai.application_id
             WHERE a.student_id = #{studentId} AND a.round_no = #{round}
            """)
    int countItems(@Param("studentId") Long studentId, @Param("round") Integer round);

    @Insert("""
            INSERT INTO application (student_id, round_no, adjust_in_lab, adjust_cross, status, submitted_at)
            VALUES (#{studentId}, #{roundNo}, #{adjustInLab}, #{adjustCross}, #{status}, #{submittedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Application app);

    @Update("""
            UPDATE application
               SET adjust_in_lab = #{adjustInLab},
                   adjust_cross  = #{adjustCross},
                   status        = #{status},
                   submitted_at  = #{submittedAt}
             WHERE id = #{id}
            """)
    int update(Application app);

    @Delete("DELETE FROM application_item WHERE application_id = #{appId}")
    int deleteItems(@Param("appId") Long appId);

    @Insert("""
            INSERT INTO application_item (application_id, lab_id, mentor_id, sort_no)
            VALUES (#{applicationId}, #{labId}, #{mentorId}, #{sortNo})
            """)
    int insertItem(ApplicationItem item);
}
