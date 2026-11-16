package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 学生发起跨室转组申请 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferReq {

    public Integer round;
    /** 想转去的实训室 */
    public Long targetLabId;
    /** 想转去的导师（必须属于 targetLabId） */
    public Long targetMentorId;
    public String reason;
}