package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 导师撤销已确认的录取 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RevokeReq {

    public Integer round;
    public Long studentId;
    public String reason;
}
