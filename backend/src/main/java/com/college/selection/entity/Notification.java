package com.college.selection.entity;

import java.time.LocalDateTime;

/** 站内通知（发给具体用户，登录后在右上角「通知」里看） */
public class Notification {

    public Long id;
    public Long userId;
    public String type;
    public String title;
    public String content;
    public Boolean isRead;
    public LocalDateTime createdAt;
}
