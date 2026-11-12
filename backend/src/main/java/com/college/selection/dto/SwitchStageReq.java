package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 切换当前阶段：会把本轮其他阶段全部关闭 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwitchStageReq {

    public Integer round;
    public Integer stageNo;
    /** 保持开放多少小时，默认 4 */
    public Integer hours;
}
