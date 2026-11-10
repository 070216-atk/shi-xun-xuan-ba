package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StageUpdateReq {

    public Integer round;
    public LocalDateTime startAt;
    public LocalDateTime endAt;

    /** 公示天数，默认 1 天 */
    public Integer days;
}
