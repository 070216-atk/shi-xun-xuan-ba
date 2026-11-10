package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 公示异议 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjectionReq {

    public Integer round;
    public String reason;
}
