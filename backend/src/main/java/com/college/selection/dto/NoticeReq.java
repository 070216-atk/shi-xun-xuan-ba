package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NoticeReq {

    public Long id;
    public String title;
    public String content;
    public Boolean isTop;
    public Integer status;
}
