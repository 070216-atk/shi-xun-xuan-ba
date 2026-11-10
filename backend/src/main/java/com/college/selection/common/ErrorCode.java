package com.college.selection.common;

/** 错误码集中定义，见 framework/4.api/字段定义.md 第 6 节 */
public final class ErrorCode {

    public static final int OK = 0;

    public static final int NOT_LOGIN = 1001;
    public static final int BAD_CREDENTIALS = 1002;
    public static final int NO_PERMISSION = 1003;

    public static final int OUT_OF_WINDOW = 2001;
    public static final int TOO_MANY_INTENTS = 2002;
    public static final int QUOTA_FULL = 2003;
    public static final int ADJUST_ORDER_WRONG = 2004;
    public static final int PENDING_OBJECTION = 2005;

    public static final int SERVER_ERROR = 5000;

    private ErrorCode() {
    }
}
