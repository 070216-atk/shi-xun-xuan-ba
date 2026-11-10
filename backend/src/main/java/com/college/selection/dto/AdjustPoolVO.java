package com.college.selection.dto;

import java.util.List;

/** 管理端：调剂池 —— 左边看空位，右边看没着落的学生 */
public class AdjustPoolVO {

    public Integer round;
    public List<Vacancy> vacancies;
    public List<Candidate> candidates;

    /** 某个导师还剩几个名额 */
    public static class Vacancy {
        public Long mentorId;
        public String mentorName;
        public Long labId;
        public String labCode;
        public String labName;
        public Integer quota;
        public Integer confirmed;
        public Integer remaining;
    }

    /** 还没确认去向的学生 */
    public static class Candidate {
        public Long studentId;
        public String code;
        public String realName;
        public String className;
        public Boolean adjustInLab;
        public Boolean adjustCross;
        /** 当前手里最好的意向类型，没有则为 null */
        public String offerType;
        public String offerStatus;
        public Integer rankNo;
    }
}
