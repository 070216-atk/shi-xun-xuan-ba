package com.college.selection.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginReq {

    @NotBlank(message = "不能为空")
    public String username;

    @NotBlank(message = "不能为空")
    public String password;

    /** web = 学生/导师统一入口；admin = 管理后台 */
    public String portal;
}
