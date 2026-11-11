package com.college.selection.dto;

import java.time.LocalDateTime;

/** 「我的选拔」页需要的一次性数据：状态 + 当前阶段 + 截止时间 */
public class StudentStatusVO {

    public Integer round;
    /** NOT_APPLIED / APPLIED / OFFERED / CONFIRMED / WAITLISTED / ADJUSTING / NOT_ADMITTED */
    public String status;
    public String statusText;

    public Integer currentStage;
    public String currentStageName;
    public LocalDateTime deadline;
    public Long remainingSeconds;

    /** 本轮收到几个意向 */
    public Integer offerCount;

    /** 公示状态：NONE / PUBLISHED / ENDED */
    public String publicity;
}
