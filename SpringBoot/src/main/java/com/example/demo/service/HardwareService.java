package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Book;
import com.example.demo.entity.LendRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.BookMapper;
import com.example.demo.mapper.LendRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.utils.OneNetUtil;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.Date;

@Service
public class HardwareService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private LendRecordMapper lendRecordMapper;

    @Resource
    private OneNetUtil oneNetUtil;

    public void handleBorrow(String cardUid, String rfidCode) {
        // 1. 验证用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getCardUid, cardUid));
        if (user == null) {
            System.out.println("借书失败：用户不存在 " + cardUid);
            oneNetUtil.sendCommand("ERR_USER");
            return;
        }

        // 2. 验证书籍
        Book book = bookMapper.selectOne(new LambdaQueryWrapper<Book>().eq(Book::getRfidCode, rfidCode));
        if (book == null) {
            System.out.println("借书失败：书籍不存在 " + rfidCode);
            oneNetUtil.sendCommand("ERR_BOOK");
            return;
        }

        // 3. 写入借阅记录
        LendRecord record = new LendRecord();
        // 注意这里进行了 Integer -> Long 的转换
        record.setReaderId(Long.valueOf(user.getId()));
        record.setIsbn(book.getIsbn());
        record.setBookname(book.getName());
        record.setLendTime(new Date());
        record.setStatus("0"); // 0代表未归还
        record.setBorrownum(book.getBorrownum() + 1);
        lendRecordMapper.insert(record);

        // 4. 更新书籍状态
        book.setStatus("0"); // 设置为已借出
        book.setBorrownum(book.getBorrownum() + 1);
        bookMapper.updateById(book);

        // 5. 反馈成功
        System.out.println("借阅成功！发送绿灯指令...");
        oneNetUtil.sendCommand("OK_BORROW");
    }
}