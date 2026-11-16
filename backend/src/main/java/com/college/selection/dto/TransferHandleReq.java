package com.college.selection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 导师处理转组申请：约谈记录 + 拒绝原因 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferHandleReq {

    public String interviewNote;
    /** 仅拒绝时用 */
    public String reason;
}