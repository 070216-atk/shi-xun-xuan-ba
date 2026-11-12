package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 管理员指定安置（约谈后的"同意的去处"） */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssignPlacementReq {

    public Integer round;
    public Long studentId;
    public Long mentorId;
    /** 必填：为什么把他安排到这里（会写进 offer.remark，学生能看到） */
    public String reason;
}
