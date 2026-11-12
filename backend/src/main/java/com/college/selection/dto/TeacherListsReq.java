package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 提交三份名单：
 *   admitted         拟录取（数量 ≤ 该导师名额）
 *   waitlist         递补（顺序即顺位，第 1 个是第一个补位）
 *   adjustCandidates 内部调剂候选
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeacherListsReq {

    public Integer round;
    public List<Long> admitted;
    public List<Long> waitlist;
    public List<Long> adjustCandidates;
}
