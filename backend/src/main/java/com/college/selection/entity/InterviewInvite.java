package com.college.selection.entity;

import java.time.LocalDateTime;

/** 面试入围：导师把报名者拉进面试名单 */
public class InterviewInvite {

    public Long id;
    public Integer roundNo;
    public Long labId;
    public Long mentorId;
    public Long studentId;
    public LocalDateTime createdAt;
}
