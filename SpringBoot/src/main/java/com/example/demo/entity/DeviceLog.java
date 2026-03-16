package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("device_log")
public class DeviceLog {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String stuNum;      // 学号
    private String actionType;  // 动作类型
    private String deviceTime;  // 设备时间戳
    // createTime 数据库会自动生成，这里不用管
}