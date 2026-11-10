package com.college.selection.entity;

/**
 * 对应 sys_user 表。
 * ⚠️ 含 passwordHash，**绝不直接返回给前端**，对外一律用 UserVO。
 */
public class SysUser {

    public Long id;
    public String username;
    public String passwordHash;
    public String role;
    public String code;
    public String realName;
    public String className;
    public Long labId;
    public Long mentorId;
    public String phone;
    public Integer status;
}
