package com.college.selection.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 HS256 JWT（不引入第三方库，用 JDK 自带的 HMAC-SHA256 实现）。
 * 生成的是标准 JWT：header.payload.signature，前端/Postman 都能直接解出 payload。
 */
@Component
public class JwtUtil {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final ObjectMapper mapper = new ObjectMapper();
    private final byte[] secret;
    private final long expireSeconds;

    public JwtUtil(@Value("${selection.jwt.secret:}") String secret,
                   @Value("${selection.jwt.expire-seconds:7200}") long expireSeconds) {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException(
                    "selection.jwt.secret 未配置或少于 32 个字符。\n"
                  + "请在 backend/src/main/resources/application-local.yml 里填一段至少 32 位的随机字符串。\n"
                  + "生成方法（PowerShell）：\n"
                  + "  -join ((1..48) | ForEach-Object { [char](Get-Random -InputObject (48..57 + 65..90 + 97..122)) })");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expireSeconds = expireSeconds;
    }

    /** 签发 token */
    public String issue(Long userId, String role, String username) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", userId);
            payload.put("role", role);
            payload.put("name", username);
            payload.put("exp", System.currentTimeMillis() / 1000 + expireSeconds);

            String h = ENC.encodeToString(mapper.writeValueAsBytes(header));
            String p = ENC.encodeToString(mapper.writeValueAsBytes(payload));
            String data = h + "." + p;
            return data + "." + ENC.encodeToString(hmac(data));
        } catch (Exception e) {
            throw new IllegalStateException("签发 token 失败", e);
        }
    }

    /** 校验并解析 token，失败抛 1001 */
    public Map<String, Object> parse(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.NOT_LOGIN, "未登录");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BizException(ErrorCode.NOT_LOGIN, "token 格式错误");
        }
        try {
            String data = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(hmac(data), DEC.decode(parts[2]))) {
                throw new BizException(ErrorCode.NOT_LOGIN, "token 签名无效");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(DEC.decode(parts[1]), Map.class);
            Number exp = (Number) payload.get("exp");
            if (exp != null && exp.longValue() < System.currentTimeMillis() / 1000) {
                throw new BizException(ErrorCode.NOT_LOGIN, "登录已过期，请重新登录");
            }
            return payload;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOT_LOGIN, "token 解析失败");
        }
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    private byte[] hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
