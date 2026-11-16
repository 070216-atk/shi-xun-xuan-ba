package com.college.selection.config;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
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

        String role = String.valueOf(payload.get("role"));
        // 按路径前缀做角色隔离：只读接口也必须有权限，
        // 否则学生 token 可以直接调 /api/admin/students 把全员名单拉走。
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/admin/") && !"ADMIN".equals(role)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有学院管理员可以访问该接口");
        }
        if (uri.startsWith("/api/teacher/") && !"TEACHER".equals(role)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有导师账号可以访问该接口");
        }
        if (uri.startsWith("/api/student/") && !"STUDENT".equals(role)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有学生账号可以访问该接口");
        }

        request.setAttribute(ATTR_USER_ID, ((Number) payload.get("sub")).longValue());
        request.setAttribute(ATTR_ROLE, role);
        return true;
    }
}
