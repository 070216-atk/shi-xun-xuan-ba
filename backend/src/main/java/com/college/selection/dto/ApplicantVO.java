package com.college.selection.dto;

/** 导师端"报名学生"一行：学生信息 + 意向导师 + 两个调剂意愿 + 已有评分 */
public class ApplicantVO {

    public Long studentId;
    public String code;
    public String realName;
    public String className;

    /** 该学生填报的导师（本室内） */
    public Long mentorId;
    public String mentorName;

    public Boolean adjustInLab;
    public Boolean adjustCross;

    // ---- 评分（未评分为 null）----
    public Integer scoreAttitude;
    public Integer scoreLogic;
    public Integer scoreCoding;
    public Integer scoreTime;
    public Double scoreTotal;
    public String scoreComment;

    public Boolean interviewed;
}
