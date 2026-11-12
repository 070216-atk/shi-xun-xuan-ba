package com.college.selection.entity;

import java.time.LocalDateTime;

/** 对应 publicity 表：公示期默认 1 天，有未处理异议不能结束 */
public class Publicity {

    public Long id;
    public Integer roundNo;
    /** DRAFT / PUBLISHED / ENDED */
    public String status;
    public LocalDateTime startAt;
    public LocalDateTime endAt;
    public Integer objectionCount;
    public Long publishedBy;
    public LocalDateTime publishedAt;
}
