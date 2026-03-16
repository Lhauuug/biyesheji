package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.List;

@Data
@TableName("category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private Integer parentId;

    // 💡 重点：这是一个非数据库字段，专门用来在内存里装载它的子节点列表
    @TableField(exist = false)
    private List<Category> children;
}