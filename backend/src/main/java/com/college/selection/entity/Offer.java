package com.college.selection.entity;

import java.time.LocalDateTime;

/** 对应 offer 表：拟录取 / 递补 / 调剂候选，三种共一张表 */
public class Offer {

    public Long id;
    public Long studentId;
    public Long labId;
    public Long mentorId;
    public Integer roundNo;
    /** ADMITTED 拟录取 / WAITLIST 递补 / ADJUST_CANDIDATE 调剂候选 */
    public String type;
    /** 递补顺位或候选序号 */
    public Integer rankNo;
    /** ISSUED / CONFIRMED / DECLINED / EXPIRED / VOID */
    public String status;
    public LocalDateTime issuedAt;
    public LocalDateTime confirmDeadline;
    public LocalDateTime confirmedAt;
    public String remark;

    /** 仅 ADJUST_CANDIDATE 用：导师指定的接收方导师 */
    public Long targetMentorId;

    // 非表字段，由 SQL join 或 service 填充
    public String labCode;
    public String labName;
    public String mentorName;
    public String studentName;
    public String studentCode;
    /** 调剂目标导师姓名（join 出来） */
    public String targetMentorName;
    /** 该生在本室的面试综合分 */
    public Double scoreTotal;
    /** 距离确认截止还有多少秒 */
    public Long remainingSeconds;
}
