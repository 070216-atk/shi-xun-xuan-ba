package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 自动录取。
 * sortBy = 'score'  → 按面试总分从高到低
 * sortBy = 'manual' → 按导师已有的排序（rank_no），没有排序的按分数排在后面
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoAdmitReq {

    public Integer round;
    public String sortBy;
}
