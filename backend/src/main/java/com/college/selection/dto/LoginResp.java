package com.college.selection.dto;

/** 登录响应：token + 有效期（秒）+ 用户信息 */
public class LoginResp {

    public String token;
    public long expiresIn;
    public UserVO user;
}
