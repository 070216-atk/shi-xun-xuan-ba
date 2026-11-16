package com.college.selection.mapper;

import com.college.selection.entity.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Insert("""
            INSERT INTO notification (user_id, type, title, content)
            VALUES (#{userId}, #{type}, #{title}, #{content})
            """)
    int insert(@Param("userId") Long userId,
               @Param("type") String type,
               @Param("title") String title,
               @Param("content") String content);

    @Insert("""
            INSERT INTO notification (user_id, type, title, content)
            SELECT id, #{type}, #{title}, #{content}
              FROM sys_user WHERE role = 'STUDENT' AND status = 1
            """)
    int insertForAllStudents(@Param("type") String type,
                             @Param("title") String title,
                             @Param("content") String content);

    @Select("""
            SELECT * FROM notification
             WHERE user_id = #{userId}
             ORDER BY id DESC
             LIMIT 50
            """)
    List<Notification> findByUser(@Param("userId") Long userId);

    @Update("UPDATE notification SET is_read = 1 WHERE user_id = #{userId}")
    int markAllRead(@Param("userId") Long userId);
}
