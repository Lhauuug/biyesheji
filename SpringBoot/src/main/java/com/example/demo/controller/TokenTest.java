//生成token
package com.example.demo.controller;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.util.Base64;

public class TokenTest {
    public static void main(String[] args) throws Exception {
        // ================= 🔴 这里要填你的信息 =================
        // 1. 产品 ID
        String productId = "7QSeidAe52";

        // 2. 你的 Access Key (去 OneNet 网页点击“查看”复制那一长串)
        // ⚠️ 重点：复制进去后，检查双引号里开头结尾千万别有空格！！！
        String key = "o3dd3xkfb8ud6U3vw4t3B0nVHVEJe8Fri5NfdfnLY1s=";
        // (👆 我从你之前的截图里抄下来的，如果不放心，你可以重新去网页复制覆盖它)
        // ======================================================

        // 资源路径：使用产品级权限 (products/产品ID)
        String res = "products/" + productId;

        // 过期时间：设为 1 年后 (避免 expire too long 报错)
        long et = System.currentTimeMillis() / 1000 + 31536000;
        String method = "md5";

        // 计算签名
        String signStr = et + "\n" + method + "\n" + res + "\n" + "2018-10-31";
        Mac mac = Mac.getInstance("HmacMD5");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacMD5"));
        String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(mac.doFinal(signStr.getBytes())), "UTF-8");

        // 拼接 Token
        String token = "version=2018-10-31&res=" + URLEncoder.encode(res, "UTF-8") + "&et=" + et + "&method=" + method + "&sign=" + sign;

        System.out.println("\n====== 👇 请复制下面这一长串 👇 ======");
        System.out.println(token);
        System.out.println("====================================\n");
    }
}