package com.college.selection.entity;

import java.time.LocalDateTime;

/** 对应 stage 表：一个轮次 × 一个环节 = 一条时间窗 */
public class Stage {

    public Long id;
    public Integer roundNo;
    public Integer stageNo;
    public String name;
    public LocalDateTime startAt;
    public LocalDateTime endAt;
    public String description;
    public Integer isOpen;

    // ---------- 以下由 service 按"当前时间"计算，不是表字段 ----------
    /** 现在是否在时间窗内 */
    public Boolean open;
    /** 是否当前所处阶段 */
    public Boolean isCurrent;
    /** 距离结束还有多少秒（未开始或已结束时为 null） */
    public Long remainingSeconds;
}
