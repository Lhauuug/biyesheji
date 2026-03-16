package com.example.demo.component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Component
@EnableScheduling // 1. 开启定时任务
public class MailTask {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 从配置文件读取你的QQ邮箱
    @Value("${spring.mail.username}")
    private String fromEmail;

    // 🕒 这里的逻辑是：每隔 30 秒执行一次 (为了方便你现在立刻看到效果！)
    // 等测试成功后，你可以改成每天早上8点: @Scheduled(cron = "0 0 8 * * ?")
    // @Scheduled(fixedRate = 30000)
    @Scheduled(cron = "0 0 8 * * ?")
    public void checkOverdueBooks() {
        System.out.println("⏰ [定时任务] 正在检查是否有逾期图书...");

        // === 核心 SQL：查询逾期记录 ===
        // 逻辑：查找应还日期 (back_date) 小于今天，且用户有邮箱的记录
        String sql = "SELECT u.email, u.nick_name, b.name AS book_name, l.back_date " +
                "FROM lend_list l " +
                "LEFT JOIN book b ON l.book_id = b.id " +
                "LEFT JOIN user u ON l.reader_id = u.id " + // ⚠️ 注意：这里要求借阅表里的 reader_id 必须等于用户表的 id
                "WHERE l.back_date < CURDATE() " + // 逾期判断
                "AND u.email IS NOT NULL";

        List<Map<String, Object>> overdueList = jdbcTemplate.queryForList(sql);

        if (overdueList.isEmpty()) {
            System.out.println("✅ [查询结果] 暂无逾期图书 (或数据未关联正确)。");
            return;
        }

// 遍历发送邮件并冻结账号
        for (Map<String, Object> record : overdueList) {
            String toEmail = (String) record.get("email");
            String nickName = (String) record.get("nick_name");
            String bookName = (String) record.get("book_name");
            // 获取数据库里的应还日期 (例如 "2026-03-10" 或 "2026-03-10 00:00:00")
            String dateStr = record.get("back_date").toString();

            // 1. 截取纯日期部分并转换为 LocalDate 对象
            String justDate = dateStr.length() > 10 ? dateStr.substring(0, 10) : dateStr;
            java.time.LocalDate backDate = java.time.LocalDate.parse(justDate);

            // 2. 计算已经逾期了几天（也就是提醒了多少次）
            long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(backDate, java.time.LocalDate.now());

            System.out.println("🚀 发现逾期！当前逾期天数：" + overdueDays + " 天，准备发送邮件给：" + toEmail);

            // 3. 无论逾期几天，每天都发一封催还邮件
            sendEmail(toEmail, nickName, bookName, dateStr);

            // 4. 👇👇 核心逻辑：只有当逾期天数大于等于 3 天时，才执行冻结！ 👇👇
            if (overdueDays >= 3) {
                Object idObj = record.get("id"); // 从 record 中获取用户 ID
                if (idObj != null) {
                    Integer userId = Integer.parseInt(idObj.toString());
                    String updateSql = "UPDATE user SET status = 0 WHERE id = ?";
                    jdbcTemplate.update(updateSql, userId);
                    System.out.println(">>> 🚨 严重警告：用户 ID [" + userId + "] 已逾期 " + overdueDays + " 天（达3次提醒），账号已被自动冻结！");
                }
            }
        }
    }

    // 发送邮件的工具方法
    private void sendEmail(String to, String name, String book, String date) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail); // 发件人
            message.setTo(to);          // 收件人
            message.setSubject("【图书馆提醒】您有一本图书已逾期！");
            message.setText("亲爱的 " + (name==null?"同学":name) + " ：\n\n" +
                    "温馨提醒：您借阅的图书 《" + (book==null?"未知图书":book) + "》 \n" +
                    "应还日期为： " + date + "。\n" +
                    "目前已逾期，请尽快归还。\n\n" +
                    "—— 图书馆管理系统自动通知");

            mailSender.send(message);
            System.out.println("📧 邮件发送成功！已投递至: " + to);
        } catch (Exception e) {
            System.err.println("❌ 邮件发送失败: " + e.getMessage());
        }
    }
}