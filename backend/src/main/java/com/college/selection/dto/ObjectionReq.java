package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 公示异议 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjectionReq {

    public Integer round;
    /** 被提出异议的学生学号（可空，允许只写原因） */
    public String studentCode;
    public String reason;
}
