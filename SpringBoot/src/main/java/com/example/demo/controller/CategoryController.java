package com.example.demo.controller;

import com.example.demo.commom.Result;
import com.example.demo.entity.Category;
import com.example.demo.mapper.CategoryMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/category")
public class CategoryController {

    @Resource
    private CategoryMapper categoryMapper;

    /**
     * 核心接口：获取分类树
     */
    @GetMapping("/tree")
    public Result<?> getTree() {
        // 1. 查出数据库里所有的分类数据 (只查一次库，性能最高)
        List<Category> allCategories = categoryMapper.selectList(null);

        // 2. 极其优雅的 Java 8 Stream 流组装树形结构
        List<Category> tree = allCategories.stream()
                .filter(category -> category.getParentId() == 0) // 第一步：过滤出所有的顶级节点 (父ID为0)
                .peek(category -> {
                    // 第二步：为每个顶级节点找子节点
                    category.setChildren(getChildren(category, allCategories));
                })
                .collect(Collectors.toList());

        return Result.success(tree);
    }

    /**
     * 递归找子节点的辅助方法
     */
    private List<Category> getChildren(Category root, List<Category> all) {
        return all.stream()
                .filter(category -> category.getParentId().equals(root.getId())) // 找出父ID等于当前节点ID的子分类
                .peek(category -> {
                    // 递归寻找下一级 (哪怕有100级也能自动找下去)
                    category.setChildren(getChildren(category, all));
                })
                .collect(Collectors.toList());
    }
}