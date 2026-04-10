package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.entity.*;
import com.example.demo.mapper.BookWithUserMapper;
import com.example.demo.mapper.DeviceLogMapper;
import com.example.demo.mapper.LendRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

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
    private BookWithUserMapper bookwithUserMapper;
    @Autowired
    private LendRecordMapper lendRecordMapper;

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
    public List<DeviceLog> getHistory(@RequestParam(required = false, defaultValue = "1")String stu_num) {
        // 按 ID 倒序查前 20 条（最新的在最上面）
        QueryWrapper<DeviceLog> query = new QueryWrapper<>();
        query.eq("stu_num", stu_num).orderByDesc("id").last("LIMIT 20");
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
                    // 1. 唯一性检查
                    if (deviceLogMapper.selectCount(new QueryWrapper<DeviceLog>().eq("device_time", timeVal)) == 0) {
                        User user = userMapper.selectOne(new QueryWrapper<User>().eq("card_uid", stuNumVal));

                        if (user != null) {
                            String targetRfid = null;
                            boolean isBorrow = false;

                            // 核心协议：1借3还(B001)，2借4还(B002)
                            switch (sendVal) {
                                case "1": targetRfid = "B001"; isBorrow = true; break;
                                case "3": targetRfid = "B001"; isBorrow = false; break;
                                case "2": targetRfid = "B002"; isBorrow = true; break;
                                case "4": targetRfid = "B002"; isBorrow = false; break;
                            }

                            if (targetRfid != null) {
                                Book book = bookMapper.selectOne(new QueryWrapper<Book>().eq("rfid_code", targetRfid));
                                if (book != null) {
                                    if (isBorrow) {
                                        // ============= 【借书业务】 =============
                                        try {
                                            // 调你现有的底层借书方法
                                            hardwareService.handleBorrow(stuNumVal, targetRfid);

                                            // 同步到前端表 bookwithuser (首页大屏显示)
                                            BookWithUser bu = new BookWithUser();
                                            bu.setId(user.getId());
                                            bu.setIsbn(book.getIsbn());
                                            bu.setBookName(book.getName());
                                            bu.setNickName(user.getNickName());
                                            Date now = new Date();
                                            bu.setLendtime(now);
                                            Calendar cal = Calendar.getInstance();
                                            cal.setTime(now);
                                            cal.add(Calendar.DAY_OF_YEAR, 30);
                                            bu.setDeadtime(cal.getTime());
                                            bu.setProlong(1);
                                            bookwithUserMapper.insert(bu);

                                            // 👇👇 新增：往 lend_record 表里写一条真正的借阅历史 👇👇
                                            LendRecord lendRecord = new LendRecord();
                                            lendRecord.setReaderId(Long.valueOf(user.getId()));
                                            lendRecord.setIsbn(book.getIsbn());
                                            lendRecord.setBookname(book.getName());
                                            lendRecord.setLendTime(now);
                                            lendRecord.setStatus("0"); // 0 代表正在借阅
                                            lendRecord.setBorrownum((book.getBorrownum() == null ? 0 : book.getBorrownum()) + 1);
                                            lendRecordMapper.insert(lendRecord);
                                            // 👆👆 新增结束 👆👆

                                            // 重点：将书的状态改为已借阅 (0)
                                            book.setStatus("0");
                                            book.setBorrownum((book.getBorrownum() == null ? 0 : book.getBorrownum()) + 1);
                                            bookMapper.updateById(book);

                                            System.out.println(">>> ✅ 借书同步成功：" + book.getName());
                                        } catch (Exception e) {
                                            System.err.println(">>> ❌ 借书拦截：" + e.getMessage());
                                        }
                                    } else {
                                        // ============= 【还书业务】 =============
                                        try {
                                            // 1. 从前端展示表移除借阅记录 (首页大屏消失)
                                            bookwithUserMapper.delete(new QueryWrapper<BookWithUser>()
                                                    .eq("id", user.getId())
                                                    .eq("book_name", book.getName()));

                                            // 👇👇 新增：把 lend_record 表里的历史记录标记为已还 👇👇
                                            LendRecord lendRecord = lendRecordMapper.selectOne(new QueryWrapper<LendRecord>()
                                                    .eq("reader_id", user.getId())
                                                    .eq("isbn", book.getIsbn())
                                                    .eq("status", "0") // 找到那条还没还的记录
                                                    .last("LIMIT 1"));

                                            if (lendRecord != null) {
                                                lendRecord.setStatus("1"); // 1 代表已归还
                                                lendRecord.setReturnTime(new Date());
                                                lendRecordMapper.updateById(lendRecord);
                                            }
                                            // 👆👆 新增结束 👆👆

                                            // 2. 重点：将书的状态恢复为未借阅 (1)
                                            book.setStatus("1");
                                            bookMapper.updateById(book);

                                            System.out.println(">>> ✅ 还书成功：" + book.getName());
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            System.err.println(">>> ❌ 还书失败：" + e.getMessage());
                                        }
                                    }
                                } else {
                                    // 预防空指针：如果数据库没找到这本书，给出明确提示
                                    System.err.println(">>> ❌ 致命错误：在 book 表里找不到 rfid_code 为 [" + targetRfid + "] 的书！请检查数据库！");
                                }
                            }
                        }
                        // 记录流水
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