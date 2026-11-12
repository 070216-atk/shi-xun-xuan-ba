package com.college.selection.entity;

/** 对应 mentor 表。名额的真实归属在这里，不在 lab 上。 */
public class Mentor {

    public Long id;
    public Long labId;
    public String name;
    public String title;
    public String intro;
    /** 该导师招收名额 */
    public Integer quota;
    public Integer sortNo;
}
