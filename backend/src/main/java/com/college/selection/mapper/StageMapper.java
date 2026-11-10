package com.college.selection.mapper;

import com.college.selection.entity.Stage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StageMapper {

    @Select("SELECT * FROM stage WHERE round_no = #{round} AND is_open = 1 ORDER BY stage_no")
    List<Stage> findByRound(@Param("round") Integer round);
}
