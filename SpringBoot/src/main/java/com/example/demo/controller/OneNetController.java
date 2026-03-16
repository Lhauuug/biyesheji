package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.entity.DeviceLog;
import com.example.demo.entity.User;
import com.example.demo.mapper.DeviceLogMapper;
import com.example.demo.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.HashMap; // 👈 新加的
import java.util.Map;     // 👈 新加的

@RestController
@RequestMapping("/onenet")
@CrossOrigin
public class OneNetController {

    // 1. 注入 Mapper，用来操作数据库
    @Autowired
    private DeviceLogMapper deviceLogMapper;

    @Autowired
    private UserMapper userMapper;

    // 2. 你的 Token (之前生成的那个，如果过期了记得换)
    private static final String AUTHORIZATION = "version=2018-10-31&res=products%2F7QSeidAe52&et=1801933406&method=md5&sign=CFR5i5pZWAXwruYkrNF%2BhQ%3D%3D";
    private static final String PRODUCT_ID = "7QSeidAe52";
    private static final String DEVICE_NAME = "test_A204";

    // 工具：用来解析 JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 接口1：获取实时数据（并尝试自动保存）
     * 前端每3秒调一次这个，我们就在这里顺便把数据存了
     */
    @GetMapping("/data")
    public String getOneNetData() {
        String urlString = "http://iot-api.heclouds.com/thingmodel/query-device-property?product_id=" + PRODUCT_ID + "&device_name=" + DEVICE_NAME;
        StringBuilder result = new StringBuilder();
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setRequestProperty("Authorization", AUTHORIZATION);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) result.append(line);
                in.close();

                // === 关键逻辑：获取数据后，尝试保存到数据库 ===
                String jsonStr = result.toString();
                saveToDbIfNew(jsonStr);

                return jsonStr;
            } else {
                return "{\"code\": 500, \"msg\": \"OneNet API Error: " + conn.getResponseCode() + "\"}";
            }
        } catch (Exception e) {
            return "{\"code\": 500, \"msg\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 接口2：获取历史记录列表（给前端表格用）
     */
    @GetMapping("/history")
    public List<DeviceLog> getHistory() {
        // 按 ID 倒序查前 20 条（最新的在最上面）
        QueryWrapper<DeviceLog> query = new QueryWrapper<>();
        query.orderByDesc("id").last("LIMIT 20");
        return deviceLogMapper.selectList(query);
    }
    /**
     * 接口3：首页统计图表数据 (借阅 vs 归还)
     * 统计 device_log 表里的真实数据
     */
    /**
     * 接口3：首页统计图表数据 (借阅 vs 归还)
     * 统计 device_log 表里的真实数据
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        // 1. 统计借阅 (改用 Long 接收)
        QueryWrapper<DeviceLog> borrowQuery = new QueryWrapper<>();
        borrowQuery.in("action_type", "1", "2");
        Long borrowCount = deviceLogMapper.selectCount(borrowQuery); // 👈 改成了 Long

        // 2. 统计归还 (改用 Long 接收)
        QueryWrapper<DeviceLog> returnQuery = new QueryWrapper<>();
        returnQuery.in("action_type", "3", "4");
        Long returnCount = deviceLogMapper.selectCount(returnQuery); // 👈 改成了 Long

        // 3. 打包返回给前端
        Map<String, Object> map = new HashMap<>();
        map.put("borrow", borrowCount);
        map.put("ret", returnCount);
        return map;
    }

    // 辅助方法：解析 JSON 并保存
    private void saveToDbIfNew(String jsonStr) {
        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            // 只有当 code=0 (成功) 且有 data 时才处理
            if (root.has("code") && root.get("code").asInt() == 0 && root.has("data")) {
                JsonNode data = root.get("data");
                String sendVal = null;
                String stuNumVal = null;
                String timeVal = null;

                // 遍历 data 数组，提取字段
                for (JsonNode item : data) {
                    String iden = item.get("identifier").asText();
                    if ("send".equals(iden)) {
                        sendVal = item.get("value").asText();
                        timeVal = item.get("time").asText(); // 操作发生的时间
                    }
                    if ("stu_num".equals(iden)) {
                        stuNumVal = item.get("value").asText();
                    }
                }

                // 只有当数据完整，且数据库里没有这条时间记录时，才保存（去重）
                if (timeVal != null && sendVal != null) {
                    QueryWrapper<DeviceLog> query = new QueryWrapper<>();
                    query.eq("device_time", timeVal);
                    if (deviceLogMapper.selectCount(query) == 0) {

                        // 👇👇 核心硬件拦截逻辑开始 👇👇
                        // 假设 sendVal 为 "1" 代表借书操作
                        if ("1".equals(sendVal) && stuNumVal != null) {

                            // 去数据库查一下这个学号/卡号对应的用户
                            QueryWrapper<User> userQuery = new QueryWrapper<>();
                            // 注意：如果硬件传来的 stuNumVal 是卡号，请把下面的 "username" 改成 "card_uid"
                            userQuery.eq("username", stuNumVal);
                            User user = userMapper.selectOne(userQuery);

                            // 如果查到了该用户，并且他的状态 status == 0 (已冻结)
                            if (user != null && user.getStatus() != null && user.getStatus() == 0) {
                                sendVal = "5"; // 强制把动作类型改为 5，代表“借阅被拒”
                                System.out.println(">>> 🚨 硬件拦截报警：学号 [" + stuNumVal + "] 账号已冻结，硬件端拒绝出库！");
                            }
                        }
                        // 👆👆 核心硬件拦截逻辑结束 👆👆

                        // 继续执行保存逻辑
                        DeviceLog log = new DeviceLog();
                        log.setDeviceTime(timeVal);
                        log.setActionType(sendVal);
                        log.setStuNum(stuNumVal);
                        deviceLogMapper.insert(log);
                        System.out.println(">>> 成功归档一条新记录，时间：" + timeVal);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("保存数据失败: " + e.getMessage());
        }
    }

}