package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 三级调剂的操作请求 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdjustReq {

    public Integer round;
    /** 安排到哪个导师 */
    public Long mentorId;
    /** 安排哪几个学生 */
    public List<Long> studentIds;

    // ---- 仅"约谈"用 ----
    public String conclusion;
}
