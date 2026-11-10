package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 提交/修改意向的请求体 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationReq {

    /** 轮次，默认 1 */
    public Integer round;

    /** 最多 3 条平行意向，不分先后 */
    @NotNull(message = "意向不能为空")
    public List<Item> items;

    /** 是否服从实训室内部调剂 */
    public Boolean adjustInLab;

    /** 是否服从全院跨实训室调剂 */
    public Boolean adjustCross;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        public Long labId;
        public Long mentorId;
    }
}
