package com.college.selection.mapper;

import com.college.selection.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysUserMapper {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND status = 1")
    SysUser findByUsername(@Param("username") String username);

    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    SysUser findById(@Param("id") Long id);

    /** seed.sql 里密码是占位符，启动时把这些人找出来改成真实 BCrypt */
    @Select("SELECT * FROM sys_user WHERE password_hash LIKE '$2a$10$REPLACE%'")
    List<SysUser> findPlaceholderUsers();

    @Update("UPDATE sys_user SET password_hash = #{hash} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("hash") String hash);
}
