package com.college.selection.common;

/** 业务异常：service 层抛出，由 GlobalExceptionHandler 统一转成 Result */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
