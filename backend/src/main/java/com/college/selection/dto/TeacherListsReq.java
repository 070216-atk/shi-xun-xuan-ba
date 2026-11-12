package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 提交三份名单：
 *   admitted         拟录取（数量 ≤ 该导师名额，数组顺序即顺位）
 *   waitlist         递补（顺序即顺位，第 1 个是第一个补位）
 *   adjustList       内部调剂候选（**带目标导师**，新流程：由导师指定调给谁）
 *   adjustCandidates 旧字段，只给不带目标导师的旧调用留兼容
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeacherListsReq {

    public Integer round;
    public List<Long> admitted;
    public List<Long> waitlist;

    /** 新：内部调剂候选 + 导师指定的接收方 */
    public List<AdjustItem> adjustList;

    /** 旧：只列学生，不指定接收方（仍支持，管理员可在统筹页补指定） */
    public List<Long> adjustCandidates;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdjustItem {
        public Long studentId;
        /** 调剂给谁；为空表示只进候选池，由学院统筹时再指定 */
        public Long targetMentorId;
    }
}
