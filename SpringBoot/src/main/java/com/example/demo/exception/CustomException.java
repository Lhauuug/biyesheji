package com.example.demo.exception;

/**
 * 自定义业务异常类
 */
public class CustomException extends RuntimeException {
    private String code;

    public CustomException(String code, String msg) {
        super(msg);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}