package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("sys_log")
public class SysLog {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;    // 操作人
    private String operation;   // 操作模块/动作 (比如："新增图书")
    private String method;      // 请求的方法名
    private String ip;          // 操作人IP
    private Long time;          // 方法执行耗时(毫秒)
    private Date createTime;    // 操作时间
}