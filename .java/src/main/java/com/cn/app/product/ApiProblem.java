package com.cn.app.product;

public class ApiProblem extends RuntimeException {
    public final int status;
    public final String errorCode;

    public ApiProblem(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
