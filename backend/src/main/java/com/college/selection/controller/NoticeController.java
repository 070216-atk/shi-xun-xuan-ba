package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.config.LoginInterceptor;
import com.college.selection.dto.NoticeReq;
import com.college.selection.entity.Notice;
import com.college.selection.service.NoticeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 公开：首页通知公告 */
    @GetMapping("/notices")
    public Result<List<Notice>> published() {
        return Result.ok(noticeService.published());
    }

    /** 管理端：全部公告 */
    @GetMapping("/admin/notices")
    public Result<List<Notice>> all(@RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId) {
        return Result.ok(noticeService.all(userId));
    }

    /** 管理端：新增 / 修改（带 id 就是改） */
    @PostMapping("/admin/notices")
    public Result<Notice> save(@RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
                               @RequestBody NoticeReq req) {
        return Result.ok(noticeService.save(userId, req));
    }

    @DeleteMapping("/admin/notices/{id}")
    public Result<Void> delete(@RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
                               @PathVariable Long id) {
        noticeService.delete(userId, id);
        return Result.ok(null);
    }
}
