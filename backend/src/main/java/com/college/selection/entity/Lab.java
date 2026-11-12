package com.college.selection.entity;

import java.util.List;

/**
 * 对应 lab 表。
 * 用 public 字段而不是 getter/setter：MyBatis 的 Reflector 和 Jackson 都支持字段访问，
 * 一个表十几个字段时能省掉一半样板代码。
 */
public class Lab {

    public Long id;
    public String code;
    public String name;
    public String direction;
    public String training;
    public String assessment;
    public String location;
    public String contact;
    public String openDay;
    /** 总名额（= 各导师名额之和，仅展示用） */
    public Integer quota;
    public Integer status;

    // ---------- 以下不是表字段，由 service 填充 ----------
    /** 本轮已报名人数 */
    public Integer applied;
    /** 详情页才返回 */
    public List<Mentor> mentors;
}
