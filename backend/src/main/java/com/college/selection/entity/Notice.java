package com.college.selection.entity;

import java.time.LocalDateTime;

/** 通知公告（管理端发布，前台展示） */
public class Notice {

    public Long id;
    public String title;
    public String content;
    public Long publisherId;
    public Boolean isTop;
    /** 1 发布 / 0 下线 */
    public Integer status;
    public LocalDateTime createdAt;

    // join 出来
    public String publisherName;
}
