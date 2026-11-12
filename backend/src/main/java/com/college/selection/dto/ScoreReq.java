package com.college.selection.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 四维度面试评分，每维 1–5 */
public class ScoreReq {

    public Integer round;

    @NotNull(message = "不能为空") @Min(value = 1, message = "范围 1-5") @Max(value = 5, message = "范围 1-5")
    public Integer attitude;

    @NotNull(message = "不能为空") @Min(value = 1, message = "范围 1-5") @Max(value = 5, message = "范围 1-5")
    public Integer logic;

    @NotNull(message = "不能为空") @Min(value = 1, message = "范围 1-5") @Max(value = 5, message = "范围 1-5")
    public Integer coding;

    @NotNull(message = "不能为空") @Min(value = 1, message = "范围 1-5") @Max(value = 5, message = "范围 1-5")
    public Integer time;

    public String comment;
}
