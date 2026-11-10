package com.college.selection.dto;

import com.college.selection.entity.SysUser;

/** 对外暴露的用户信息，**不含密码** */
public class UserVO {

    public Long id;
    public String username;
    public String role;
    public String code;
    public String realName;
    public String className;
    public Long labId;
    public Long mentorId;

    public static UserVO of(SysUser u) {
        UserVO v = new UserVO();
        v.id = u.id;
        v.username = u.username;
        v.role = u.role;
        v.code = u.code;
        v.realName = u.realName;
        v.className = u.className;
        v.labId = u.labId;
        v.mentorId = u.mentorId;
        return v;
    }
}
