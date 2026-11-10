package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.config.LoginInterceptor;
import com.college.selection.dto.AdjustPoolVO;
import com.college.selection.dto.AdjustReq;
import com.college.selection.dto.ObjectionReq;
import com.college.selection.dto.QuotaVO;
import com.college.selection.dto.StageUpdateReq;
import com.college.selection.entity.Publicity;
import com.college.selection.entity.Stage;
import com.college.selection.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 学院管理端：名额总览、阶段配置、三级调剂、公示、看板 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/quotas")
    public Result<List<QuotaVO>> quotas(@RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(adminService.quotas(round));
    }

    @GetMapping("/stages")
    public Result<List<Stage>> stages(@RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(adminService.stages(round));
    }

    @PutMapping("/stages/{id}")
    public Result<Void> updateStage(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long id,
            @RequestBody StageUpdateReq req) {
        adminService.updateStage(userId, id, req.startAt, req.endAt);
        return Result.ok(null);
    }

    /** 调剂池：空位 + 待安置学生 */
    @GetMapping("/adjust/pool")
    public Result<AdjustPoolVO> pool(@RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(adminService.pool(round));
    }

    /** ① 室内调剂 */
    @PostMapping("/adjust/internal")
    public Result<Void> adjustInternal(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody AdjustReq req) {
        adminService.adjustInternal(userId, round(req), req.mentorId, req.studentIds);
        return Result.ok(null);
    }

    /** ② 跨室调剂（必须等第一优先级做完） */
    @PostMapping("/adjust/cross")
    public Result<Void> adjustCross(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody AdjustReq req) {
        adminService.adjustCross(userId, round(req), req.mentorId, req.studentIds);
        return Result.ok(null);
    }

    /** ③ 约谈安置（必须等前两级做完） */
    @PostMapping("/adjust/talk")
    public Result<Void> adjustTalk(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody AdjustReq req) {
        if (req.studentIds == null || req.studentIds.isEmpty()) {
            return Result.fail(2008, "必须指定学生");
        }
        adminService.adjustTalk(userId, round(req), req.mentorId, req.studentIds.get(0), req.conclusion);
        return Result.ok(null);
    }

    /** 发布公示，默认 1 天 */
    @PostMapping("/publicity/publish")
    public Result<Publicity> publish(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody(required = false) StageUpdateReq req) {
        int round = (req == null || req.round == null) ? 1 : req.round;
        Integer days = (req == null) ? 1 : req.days;
        return Result.ok(adminService.publish(userId, round, days));
    }

    /** 结束公示：有未处理异议时拒绝 */
    @PostMapping("/publicity/end")
    public Result<Void> end(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        adminService.endPublicity(userId, round);
        return Result.ok(null);
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(adminService.dashboard(round));
    }

    /** 提交一条公示异议（登记用，处理在线下） */
    @PostMapping("/publicity/objections")
    public Result<Void> addObjection(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody ObjectionReq req) {
        adminService.addObjection(userId, req.round == null ? 1 : req.round, req.reason);
        return Result.ok(null);
    }

    /** 异议处理完，清零；清零后 /publicity/end 才能通过 */
    @PostMapping("/publicity/objections/resolve")
    public Result<Void> resolveObjections(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        adminService.resolveObjections(userId, round);
        return Result.ok(null);
    }

    /** 开启下一轮补录（第 2 / 3 轮） */
    @PostMapping("/rounds")
    public Result<Void> openRound(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody AdjustReq req) {
        adminService.openRound(userId, req.round);
        return Result.ok(null);
    }

    private Integer round(AdjustReq req) {
        return req.round == null ? 1 : req.round;
    }
}
