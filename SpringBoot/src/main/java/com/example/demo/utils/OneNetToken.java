//AccessKey 变成 OneNet 认可的 Token
package com.example.demo.utils;

import org.apache.commons.codec.binary.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class OneNetToken {

    /**
     * 生成 OneNet Studio 鉴权 Token
     * @param version  版本号，默认 "2018-10-31"
     * @param resource 资源路径 (例如 "products/123/devices/456")
     * @param et       过期时间 (秒级时间戳)
     * @param accessKey 你的 AccessKey
     * @return 签名后的 Token
     */
    public static String assembleToken(String version, String resource, long et, String accessKey) {
        try {
            String res = URLEncoder.encode(resource, "UTF-8");
            String signStr = "et=" + et + "&method=sha1&res=" + res + "&version=" + version;
            String sign = hmacEncrypt(signStr, Base64.decodeBase64(accessKey));
            return "res=" + res + "&et=" + et + "&method=sha1&sign=" + URLEncoder.encode(sign, "UTF-8") + "&version=" + version;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String hmacEncrypt(String data, byte[] key) {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            return Base64.encodeBase64String(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}