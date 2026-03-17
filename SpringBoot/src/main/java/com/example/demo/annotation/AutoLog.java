package com.example.demo.annotation;

import java.lang.annotation.*;

/**
 * 自定义操作日志注解
 */
@Target(ElementType.METHOD) // 作用在方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时有效
@Documented
public @interface AutoLog {
    // 日志的描述，比如："删除图书"、"登录系统"
    String value() default "";
}