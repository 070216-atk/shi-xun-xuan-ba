package com.college.selection.dto;

import com.college.selection.entity.Offer;

import java.util.List;

/** 本室（本导师）三份名单 */
public class TeacherListsVO {

    public Integer round;
    public Long mentorId;
    public String mentorName;
    /** 该导师名额 */
    public Integer quota;

    public List<Offer> admitted;
    public List<Offer> waitlist;
    public List<Offer> adjustCandidates;
}
