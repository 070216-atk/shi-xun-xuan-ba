package com.college.selection.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationLogMapper {

    @Insert("""
            INSERT INTO operation_log (operator_id, operator_role, action, target_type, target_id, remark)
            VALUES (#{operatorId}, #{role}, #{action}, #{targetType}, #{targetId}, #{remark})
            """)
    int insert(@Param("operatorId") Long operatorId,
               @Param("role") String role,
               @Param("action") String action,
               @Param("targetType") String targetType,
               @Param("targetId") Long targetId,
               @Param("remark") String remark);
}
