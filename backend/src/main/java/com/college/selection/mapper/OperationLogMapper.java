package com.college.selection.mapper;

import com.college.selection.entity.OperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /** 带前后的状态变更（撤销录取这类操作用） */
    @Insert("""
            INSERT INTO operation_log (operator_id, operator_role, action, target_type, target_id,
                                       before_state, after_state, remark)
            VALUES (#{operatorId}, #{role}, #{action}, #{targetType}, #{targetId},
                    #{beforeState}, #{afterState}, #{remark})
            """)
    int insertWithState(@Param("operatorId") Long operatorId,
                        @Param("role") String role,
                        @Param("action") String action,
                        @Param("targetType") String targetType,
                        @Param("targetId") Long targetId,
                        @Param("beforeState") String beforeState,
                        @Param("afterState") String afterState,
                        @Param("remark") String remark);

    @Select("""
            <script>
            SELECT l.*, u.real_name AS operator_name
              FROM operation_log l LEFT JOIN sys_user u ON u.id = l.operator_id
             <where>
               <if test="actions != null and actions.size() > 0">
                 AND l.action IN
                 <foreach collection="actions" item="a" open="(" separator="," close=")">#{a}</foreach>
               </if>
             </where>
             ORDER BY l.id DESC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<OperationLog> page(@Param("actions") List<String> actions,
                            @Param("offset") int offset,
                            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM operation_log
             <where>
               <if test="actions != null and actions.size() > 0">
                 AND action IN
                 <foreach collection="actions" item="a" open="(" separator="," close=")">#{a}</foreach>
               </if>
             </where>
            </script>
            """)
    int count(@Param("actions") List<String> actions);

    @Select("SELECT DISTINCT action FROM operation_log ORDER BY action")
    List<String> distinctActions();

    /** 某次公示中还没处理的异议（remark 形如「学号|原因」，清零后重新计数） */
    @Select("""
            SELECT l.id AS id, l.created_at AS createdAt, l.remark AS remark,
                   u.real_name AS operatorName
              FROM operation_log l
              LEFT JOIN sys_user u ON u.id = l.operator_id
             WHERE l.action = 'OBJECTION' AND l.target_type = 'PUBLICITY' AND l.target_id = #{publicityId}
               AND l.id > COALESCE((SELECT MAX(id) FROM operation_log
                                     WHERE action = 'OBJECTION_RESOLVE'
                                       AND target_type = 'PUBLICITY' AND target_id = #{publicityId}), 0)
             ORDER BY l.id
            """)
    List<java.util.Map<String, Object>> unresolvedObjections(@Param("publicityId") Long publicityId);
}
