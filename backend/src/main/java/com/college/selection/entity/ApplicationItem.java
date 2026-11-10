package com.college.selection.entity;

/** 对应 application_item 表：一条平行意向 = 一个 (实训室, 导师) */
public class ApplicationItem {

    public Long id;
    public Long applicationId;
    public Long labId;
    public Long mentorId;
    /** 1-3，仅展示序号，不代表志愿先后 */
    public Integer sortNo;

    // 非表字段，由 SQL join 出来
    public String labCode;
    public String labName;
    public String mentorName;
}
