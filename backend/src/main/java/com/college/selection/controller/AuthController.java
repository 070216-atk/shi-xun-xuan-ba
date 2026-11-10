package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.config.LoginInterceptor;
import com.college.selection.dto.LoginReq;
import com.college.selection.dto.LoginResp;
import com.college.selection.dto.UserVO;
import com.college.selection.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req.username, req.password));
    }

    @GetMapping("/me")
    public Result<UserVO> me(@RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId) {
        return Result.ok(authService.me(userId));
    }

    /**
     * JWT 是无状态的，服务端没有会话可销毁，这个接口只是给前端一个统一的调用点
     * （顺便以后可以在这里写登出审计日志）。前端拿到 200 后自己丢掉 token 即可。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok(null);
    }
}
