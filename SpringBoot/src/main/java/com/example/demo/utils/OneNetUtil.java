package com.example.demo.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OneNetUtil {

    @Value("${onenet.product-id}")
    private String productId;

    @Value("${onenet.device-name}")
    private String deviceName;

    @Value("${onenet.access-key}")
    private String accessKey;

    public void sendCommand(String cmd) {
        // 1. 构造资源路径: products/{pid}/devices/{device_name}
        String resource = "products/" + productId + "/devices/" + deviceName;

        // 2. 计算过期时间 (当前时间 + 100天，保证 Token 只要项目跑着就有效)
        long et = System.currentTimeMillis() / 1000 + 3600 * 24 * 100;

        // 3. 生成 Token
        String token = OneNetToken.assembleToken("2018-10-31", resource, et, accessKey);

        System.out.println("生成的Token: " + token);

        // 4. 发送命令 (新版 API 地址不同！)
        // API: http://api.heclouds.com/cmds?device_id=... (这是老版)
        // API: https://open.iot.10086.cn/services/studio/cmds/v1/devices/{device_name}/commands (这是新版)

        // 注意：这里需要确认外包用的是 Studio 的哪个 API。
        // 通常 Studio 下发命令 URL 如下：
        String url = "https://open.iot.10086.cn/services/studio/cmds/v1/devices/"
                + deviceName
                + "/commands?project_id=" + productId;
        // 如果外包用的是 MQTT 订阅 topic $sys/{pid}/{device-name}/cmd/request/...

        try {
            HttpResponse response = HttpRequest.post(url)
                    .header("Authorization", token) // 新版鉴权放在 Authorization 头里
                    .body(cmd)
                    .timeout(5000)
                    .execute();

            System.out.println("OneNet Studio 下发结果: " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}