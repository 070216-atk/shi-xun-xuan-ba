package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.config.LoginInterceptor;
import com.college.selection.entity.Notification;
import com.college.selection.mapper.NotificationMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 站内通知：任何已登录用户都能看自己的那几条 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationMapper notificationMapper;

    public NotificationController(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @GetMapping
    public Result<List<Notification>> list(@RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId) {
        return Result.ok(notificationMapper.findByUser(userId));
    }

    @PostMapping("/read")
    public Result<Void> markRead(@RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId) {
        notificationMapper.markAllRead(userId);
        return Result.ok(null);
    }
}
