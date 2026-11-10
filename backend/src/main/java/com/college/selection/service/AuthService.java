package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.common.JwtUtil;
import com.college.selection.dto.LoginResp;
import com.college.selection.dto.UserVO;
import com.college.selection.entity.SysUser;
import com.college.selection.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthService(SysUserMapper userMapper, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResp login(String username, String rawPassword) {
        SysUser u = userMapper.findByUsername(username);
        // 用户不存在和密码错误返回同一个错误，避免被用来探测账号是否存在
        if (u == null || !encoder.matches(rawPassword, u.passwordHash)) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "用户名或密码错误");
        }
        LoginResp resp = new LoginResp();
        resp.token = jwtUtil.issue(u.id, u.role, u.username);
        resp.expiresIn = jwtUtil.getExpireSeconds();
        resp.user = UserVO.of(u);
        return resp;
    }

    public UserVO me(Long userId) {
        SysUser u = userMapper.findById(userId);
        if (u == null) {
            throw new BizException(ErrorCode.NOT_LOGIN, "用户不存在");
        }
        return UserVO.of(u);
    }
}
