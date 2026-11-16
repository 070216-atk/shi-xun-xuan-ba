package com.college.selection.entity;

import java.time.LocalDateTime;

/**
 * 对应 transfer_request 表：已录取学生对去向不满意，申请跨实训室转组。
 * 目标导师线下约谈后接收（原去向作废、直接变成新导师的已确认录取）或拒绝。
 */
public class TransferRequest {

    public Long id;
    public Long studentId;
    public Integer roundNo;
    /** 学生当前已确认的那条录取意向 */
    public Long fromOfferId;
    public Long fromMentorId;
    public Long fromLabId;
    public Long targetLabId;
    public Long targetMentorId;
    public String reason;
    /** PENDING / ACCEPTED / REJECTED / CANCELED */
    public String status;
    public String interviewNote;
    public String rejectReason;
    public Long handledBy;
    public LocalDateTime handledAt;
    public LocalDateTime createdAt;

    // 非表字段，列表页直接显示
    public String studentName;
    public String studentCode;
    public String fromLabCode;
    public String fromLabName;
    public String fromMentorName;
    public String targetLabCode;
    public String targetLabName;
    public String targetMentorName;
}