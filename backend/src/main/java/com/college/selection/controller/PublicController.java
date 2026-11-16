package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.entity.Publicity;
import com.college.selection.mapper.AdminMapper;
import com.college.selection.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
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
     * 轮次列表（第 1 轮首轮、第 2/3 轮补录），前端顶栏据此提供轮次切换。
     */
    @GetMapping("/rounds")
    public Result<List<Map<String, Object>>> rounds() {
        return Result.ok(adminMapper.listRounds());
    }

    /**
     * 公示状态 + 最终名单。
     * 只有公示已发布（PUBLISHED）或已结束（ENDED）才返回名单，否则 data 为 null。
     * 名单里的学号做脱敏：公网可访问，不能把完整学号挂出去。
     */
    @GetMapping("/publicity")
    public Result<Map<String, Object>> publicity(@RequestParam(defaultValue = "1") Integer round) {
        Publicity p = adminMapper.latestPublicity(round);
        if (p == null || (!"PUBLISHED".equals(p.status) && !"ENDED".equals(p.status))) {
            return Result.ok(null);
        }
        List<Map<String, Object>> list = adminService.confirmedResults(round);
        for (Map<String, Object> row : list) {
            row.put("code", maskCode(row.get("code")));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", p.status);
        m.put("startAt", p.startAt);
        m.put("endAt", p.endAt);
        m.put("list", list);
        return Result.ok(m);
    }

    /** 20230101 → 2023****1：前后留一点，中间打码 */
    private String maskCode(Object code) {
        if (code == null) return null;
        String s = String.valueOf(code);
        if (s.length() <= 4) {
            return "****";
        }
        return s.substring(0, 4) + "****" + s.substring(s.length() - 1);
    }
}
