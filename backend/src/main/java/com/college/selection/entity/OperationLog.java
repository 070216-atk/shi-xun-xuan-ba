package com.college.selection.entity;

import java.time.LocalDateTime;

/** 操作日志 */
public class OperationLog {

    public Long id;
    public Long operatorId;
    public String operatorRole;
    public String action;
    public String targetType;
    public Long targetId;
    public String beforeState;
    public String afterState;
    public String remark;
    public String ip;
    public LocalDateTime createdAt;

    // join 出来
    public String operatorName;
}
