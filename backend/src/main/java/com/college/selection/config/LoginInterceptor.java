package com.college.selection.config;

import com.college.selection.common.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 登录拦截器：解析 Authorization: Bearer <token>，
 * 把 userId / role 放进 request attribute，供 controller 用 @RequestAttribute 取。
 * 校验失败直接抛 BizException(1001)，交给 GlobalExceptionHandler 转成统一返回体。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLE = "role";

    private final JwtUtil jwtUtil;

    public LoginInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        String token = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7).trim();
        }
        Map<String, Object> payload = jwtUtil.parse(token);

        request.setAttribute(ATTR_USER_ID, ((Number) payload.get("sub")).longValue());
        request.setAttribute(ATTR_ROLE, String.valueOf(payload.get("role")));
        return true;
    }
}
