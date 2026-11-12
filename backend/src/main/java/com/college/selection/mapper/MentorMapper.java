package com.college.selection.mapper;

import com.college.selection.entity.Mentor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MentorMapper {

    @Select("SELECT * FROM mentor WHERE lab_id = #{labId} ORDER BY sort_no, id")
    List<Mentor> findByLabId(@Param("labId") Long labId);

    @Select("SELECT * FROM mentor WHERE id = #{id}")
    Mentor findById(@Param("id") Long id);

    /** 该室所有导师名额之和，用来和 lab.quota 对账 */
    @Select("SELECT COALESCE(SUM(quota), 0) FROM mentor WHERE lab_id = #{labId}")
    int sumQuotaByLab(@Param("labId") Long labId);
}
