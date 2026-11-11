package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.entity.Publicity;
import com.college.selection.mapper.AdminMapper;
import com.college.selection.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开接口：不需要登录。
 * 学生想查"公示名单"就走这里，不能让他们调 /admin/results。
 */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final AdminMapper adminMapper;
    private final AdminService adminService;

    public PublicController(AdminMapper adminMapper, AdminService adminService) {
        this.adminMapper = adminMapper;
        this.adminService = adminService;
    }

    /**
     * 公示状态 + 最终名单。
     * 只有公示已发布（PUBLISHED）或已结束（ENDED）才返回名单，否则 data 为 null。
     */
    @GetMapping("/publicity")
    public Result<Map<String, Object>> publicity(@RequestParam(defaultValue = "1") Integer round) {
        Publicity p = adminMapper.latestPublicity(round);
        if (p == null || (!"PUBLISHED".equals(p.status) && !"ENDED".equals(p.status))) {
            return Result.ok(null);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", p.status);
        m.put("startAt", p.startAt);
        m.put("endAt", p.endAt);
        m.put("list", adminService.confirmedResults(round));
        return Result.ok(m);
    }
}
