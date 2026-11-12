package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 面试入围：把一批学生拉进面试名单 / 取消入围 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InviteReq {

    public Integer round;
    public List<Long> studentIds;
}
