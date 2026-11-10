package com.college.selection.entity;

import java.time.LocalDateTime;
import java.util.List;

/** 对应 application 表：一个学生一轮一条 */
public class Application {

    public Long id;
    public Long studentId;
    public Integer roundNo;
    public Boolean adjustInLab;
    public Boolean adjustCross;
    /** DRAFT / SUBMITTED */
    public String status;
    public LocalDateTime submittedAt;

    // 非表字段
    public List<ApplicationItem> items;
}
