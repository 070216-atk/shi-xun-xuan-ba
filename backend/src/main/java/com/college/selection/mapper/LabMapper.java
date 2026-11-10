package com.college.selection.mapper;

import com.college.selection.entity.Lab;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LabMapper {

    @Select("SELECT * FROM lab WHERE status = 1 ORDER BY code")
    List<Lab> findAll();

    @Select("SELECT * FROM lab WHERE id = #{id}")
    Lab findById(@Param("id") Long id);

    /** 某轮某室已提交意向的人数（去重学生） */
    @Select("""
            SELECT COUNT(DISTINCT a.student_id)
              FROM application a
              JOIN application_item ai ON ai.application_id = a.id
             WHERE ai.lab_id = #{labId}
               AND a.round_no = #{round}
               AND a.status = 'SUBMITTED'
            """)
    int countApplied(@Param("labId") Long labId, @Param("round") Integer round);
}
