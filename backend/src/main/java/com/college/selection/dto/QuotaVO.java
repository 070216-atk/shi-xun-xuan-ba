package com.college.selection.dto;

/** 管理端：一个实训室的名额总览 */
public class QuotaVO {

    public Long labId;
    public String code;
    public String name;
    /** 该室所有导师名额之和 */
    public Integer quota;
    /** 本轮报名人数（去重学生） */
    public Integer applied;
    /** 已确认（占位） */
    public Integer confirmed;
    /** 已下发但学生未确认 */
    public Integer issued;
    /** 剩余 = quota - confirmed */
    public Integer remaining;
}
