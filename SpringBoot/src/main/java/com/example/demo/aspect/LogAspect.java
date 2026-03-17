package com.example.demo.aspect;

import cn.hutool.core.date.DateUtil;
import com.example.demo.annotation.AutoLog;
import com.example.demo.entity.SysLog;
import com.example.demo.mapper.SysLogMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Date;

@Aspect      // 告诉 Spring 这是一个切面类
@Component   // 交给 Spring 容器管理
public class LogAspect {

    @Resource
    private SysLogMapper sysLogMapper;

    /**
     * 环绕通知：拦截所有带有 @AutoLog 注解的方法
     */
    @Around("@annotation(autoLog)")
    public Object around(ProceedingJoinPoint point, AutoLog autoLog) throws Throwable {
        long beginTime = System.currentTimeMillis();

        // 1. 执行原方法 (比如执行真正的"删除图书"操作)
        Object result = point.proceed();

        // 2. 原方法执行完后，计算耗时
        long time = System.currentTimeMillis() - beginTime;

        // 3. 异步记录日志到数据库 (为了不影响原方法的执行速度)
        recordLog(point, time, autoLog.value());

        return result;
    }

    private void recordLog(ProceedingJoinPoint joinPoint, long time, String operation) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        SysLog sysLog = new SysLog();

        // 设置注解上的描述 (比如："查询图书列表")
        sysLog.setOperation(operation);

        // 获取请求的方法名 (类名.方法名)
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = signature.getName();
        sysLog.setMethod(className + "." + methodName);

        // 获取用户的 IP 地址
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        sysLog.setIp(request.getRemoteAddr());

        // 获取当前登录用户名 (这里为了简化，你可以先写死测试，后面再改成从 Token 或 Session 中获取真实用户)
        sysLog.setUsername("admin");

        sysLog.setTime(time);
        sysLog.setCreateTime(new Date());

        // 插入数据库
        sysLogMapper.insert(sysLog);
    }
}