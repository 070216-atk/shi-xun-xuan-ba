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
    /** 面试时展示的项目/作品/经历（导师录入） */
    public String showcase;

    public Boolean interviewed;

    /** 本室是否已把这名学生拉进面试名单 */
    public Boolean invited;
    /** 是否是我（当前登录导师）拉进来的 */
    public Boolean invitedByMe;
}
