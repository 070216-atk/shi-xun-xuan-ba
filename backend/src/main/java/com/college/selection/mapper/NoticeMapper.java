package com.college.selection.mapper;

import com.college.selection.entity.Notice;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NoticeMapper {

    /** 前台：只看已发布的，置顶的排前面 */
    @Select("""
            SELECT n.*, u.real_name AS publisher_name
              FROM notice n LEFT JOIN sys_user u ON u.id = n.publisher_id
             WHERE n.status = 1
             ORDER BY n.is_top DESC, n.created_at DESC
             LIMIT 20
            """)
    List<Notice> findPublished();

    /** 管理端：全部 */
    @Select("""
            SELECT n.*, u.real_name AS publisher_name
              FROM notice n LEFT JOIN sys_user u ON u.id = n.publisher_id
             ORDER BY n.is_top DESC, n.created_at DESC
            """)
    List<Notice> findAll();

    @Insert("""
            INSERT INTO notice (title, content, publisher_id, is_top, status)
            VALUES (#{title}, #{content}, #{publisherId}, #{isTop}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notice n);

    @Update("""
            UPDATE notice SET title = #{title}, content = #{content}, is_top = #{isTop}, status = #{status}
             WHERE id = #{id}
            """)
    int update(Notice n);

    @Delete("DELETE FROM notice WHERE id = #{id}")
    int delete(@Param("id") Long id);

    @Select("SELECT * FROM notice WHERE id = #{id}")
    Notice findById(@Param("id") Long id);
}
