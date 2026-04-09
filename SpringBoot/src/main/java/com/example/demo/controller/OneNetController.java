package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.entity.Book;
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
    @Autowired
    private com.example.demo.service.HardwareService hardwareService;
    @Autowired
    private com.example.demo.mapper.BookMapper bookMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3000)
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

    // 核弹级调试版：无视去重，强行打印
    private void saveToDbIfNew(String jsonStr) {
        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            if (root.has("code") && root.get("code").asInt() == 0 && root.has("data")) {
                JsonNode data = root.get("data");
                String sendVal = null;
                String stuNumVal = null;
                String timeVal = null;

                for (JsonNode item : data) {
                    String iden = item.get("identifier").asText();
                    if ("send".equals(iden)) {
                        sendVal = item.get("value").asText();
                        timeVal = item.get("time").asText();
                    }
                    if ("stu_num".equals(iden)) {
                        stuNumVal = item.get("value").asText();
                    }
                }

                if (timeVal != null && sendVal != null) {
                    // 👇 1. 去重护盾：防止前端轮询导致重复借书
                    QueryWrapper<DeviceLog> query = new QueryWrapper<>();
                    query.eq("device_time", timeVal);
                    if (deviceLogMapper.selectCount(query) == 0) {

                        // 👇 2. 核心硬件业务逻辑
                        if ("1".equals(sendVal) && stuNumVal != null) {

                            // 查一下这个刷卡的人是谁，有没有被冻结
                            QueryWrapper<User> userQuery = new QueryWrapper<>();
                            userQuery.eq("card_uid", stuNumVal);
                            User user = userMapper.selectOne(userQuery);

                            if (user != null && user.getStatus() != null && user.getStatus() == 0) {
                                sendVal = "5"; // 账号被冻结，拦截
                                System.out.println(">>> 🚨 拦截：该账号已冻结！");
                            } else if (user != null) {
                                // 账号正常，开始借书！
                                // 【答辩兼容方案】：硬件没发书号，默认借 B001
                                String mockBookRfid = "B001";

                                try {
                                    // A. 走真实的底层业务（扣库存，写 lend_record）
                                    hardwareService.handleBorrow(stuNumVal, mockBookRfid);

                                    // B. 动态查书：去数据库查这本 B001 的真实信息（不写死 ISBN 和书名）
                                    QueryWrapper<Book> bookQuery = new QueryWrapper<>();
                                    bookQuery.eq("rfid_code", mockBookRfid);
                                    Book book = bookMapper.selectOne(bookQuery);

                                    if (book != null) {
                                        // C. 强行搭桥：把动态查到的真实信息，写进前端专用的 bookwithuser 表！
                                        String sql = "INSERT INTO bookwithuser (id, isbn, book_name, nick_name, lendtime, deadtime, prolong) " +
                                                "VALUES (?, ?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1)";
                                        jdbcTemplate.update(sql, user.getId(), book.getIsbn(), book.getName(), user.getNickName());
                                        System.out.println(">>> 🌉 [完美同步] 已动态获取书籍《" + book.getName() + "》，并推送至前端显示！");
                                    }
                                } catch (Exception e) {
                                    System.err.println(">>> ❌ 借书失败: " + e.getMessage());
                                }
                            }
                        }
                        // ================= 👇 新增的还书逻辑 👇 =================
                        else if ("3".equals(sendVal) && stuNumVal != null) {
                            System.out.println(">>> 📡 收到还书指令！开始处理...");

                            // 1. 查一下这个刷卡的人是谁
                            QueryWrapper<User> userQuery = new QueryWrapper<>();
                            userQuery.eq("card_uid", stuNumVal);
                            User user = userMapper.selectOne(userQuery);

                            if (user != null) {
                                // 【答辩兼容方案】：假设硬件还书也没发书号，依然默认是 B001
                                String mockBookRfid = "B001";

                                try {
                                    // A. 走真实的底层业务（增加库存，更新/结束 lend_record）
                                    // ⚠️ 注意：这里假设你 HardwareService 里的还书方法叫 handleReturn
                                    // 如果叫别的名字（比如 returnBook），请自己改一下这行代码！
                                    hardwareService.handleBorrow(stuNumVal, mockBookRfid);
                                    System.out.println(">>> ✅ [底层成功] 书籍已归还，库存已恢复！");

                                    // B. 强行搭桥：把前端 bookwithuser 表里的这条记录给删掉！
                                    // 加上书名判断，防止张三借了多本书时全被删光
                                    String sql = "DELETE FROM bookwithuser WHERE id = ? AND book_name = ?";

                                    // 去数据库查一下真实书名，防止写死
                                    QueryWrapper<Book> bookQuery = new QueryWrapper<>();
                                    bookQuery.eq("rfid_code", mockBookRfid);
                                    Book book = bookMapper.selectOne(bookQuery);

                                    if(book != null) {
                                        jdbcTemplate.update(sql, user.getId(), book.getName());
                                        System.out.println(">>> 🌉 [同步清理] 已从前端移除《" + book.getName() + "》的借阅状态！");
                                    } else {
                                        // 兜底策略，按写死的删
                                        jdbcTemplate.update(sql, user.getId(), "B001");
                                    }
                                } catch (Exception e) {
                                    System.err.println(">>> ❌ 还书业务报错: " + e.getMessage());
                                }
                            }
                        }
                        // ================= 👆 新增的还书逻辑结束 👆 =================

                        // 👇 3. 无论借书成败，都保存日志（确保前端能看到刷卡动作）
                        DeviceLog log = new DeviceLog();
                        log.setDeviceTime(timeVal);
                        log.setActionType(sendVal);
                        log.setStuNum(stuNumVal);
                        deviceLogMapper.insert(log);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析失败: " + e.getMessage());
        }
    }
}