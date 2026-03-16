package com.example.demo.controller;

import com.example.demo.entity.ReserveList;
import com.example.demo.mapper.ReserveListMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/reserve")
public class ReserveController {

    @Autowired
    private ReserveListMapper reserveListMapper;

    @PostMapping("/add")
    public Map<String, Object> addReserve(@RequestBody ReserveList reserve) {
        // 默认状态 0（排队中）
        reserve.setStatus(0);
        reserveListMapper.insert(reserve);

        Map<String, Object> res = new HashMap<>();
        res.put("code", 0);
        res.put("msg", "预约排队成功！图书归还后将邮件通知您。");
        return res;
    }
}