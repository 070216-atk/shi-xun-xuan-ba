package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.config.LoginInterceptor;
import com.college.selection.dto.ApplicantVO;
import com.college.selection.dto.AutoAdmitReq;
import com.college.selection.dto.InviteReq;
import com.college.selection.dto.RevokeReq;
import com.college.selection.dto.ScoreReq;
import com.college.selection.dto.TeacherListsReq;
import com.college.selection.dto.TeacherListsVO;
import com.college.selection.service.TeacherService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 导师端：看报名学生、四维评分、提交三份名单 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    /** 本室报名学生（不论名额满不满，全部列出） */
    @GetMapping("/applicants")
    public Result<List<ApplicantVO>> applicants(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(teacherService.applicants(userId, round));
    }

    /** 录入 / 修改面试评分（四维 1–5 + 评语） */
    @PutMapping("/scores/{studentId}")
    public Result<Void> saveScore(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long studentId,
            @Valid @RequestBody ScoreReq req) {
        teacherService.saveScore(userId, studentId, req.round == null ? 1 : req.round, req);
        return Result.ok(null);
    }

    /** 本导师三份名单 */
    @GetMapping("/lists")
    public Result<TeacherListsVO> lists(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(teacherService.lists(userId, round));
    }

    /** 提交三份名单：拟录取 / 递补（按顺序） / 内部调剂候选 */
    @PutMapping("/lists")
    public Result<Void> saveLists(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody TeacherListsReq req) {
        teacherService.saveLists(userId, req.round == null ? 1 : req.round, req);
        return Result.ok(null);
    }

    /** 撤销已确认的录取（会自动按递补顺位补位，并写操作日志） */
    @PostMapping("/lists/revoke")
    public Result<java.util.Map<String, Object>> revoke(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody RevokeReq req) {
        return Result.ok(teacherService.revoke(userId,
                req.round == null ? 1 : req.round, req.studentId, req.reason));
    }

    // ---------------- 面试入围 ----------------

    /** 把学生拉进本室面试名单（导师先初选，只有入围者才面试） */
    @PostMapping("/invites")
    public Result<Map<String, Object>> invite(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody InviteReq req) {
        return Result.ok(teacherService.invite(userId,
                req.round == null ? 1 : req.round, req.studentIds));
    }

    /** 取消入围 */
    @DeleteMapping("/invites/{studentId}")
    public Result<Map<String, Object>> uninvite(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(teacherService.uninvite(userId, round, studentId));
    }

    /** 本室面试名单（入围者的并集） */
    @GetMapping("/interview-list")
    public Result<List<ApplicantVO>> interviewList(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(teacherService.interviewList(userId, round));
    }

    // ---------------- 自动录取 ----------------

    /** 自动录取：按评分或按导师排序录满名额，其余进递补 */
    @PostMapping("/lists/auto")
    public Result<Map<String, Object>> autoAdmit(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody AutoAdmitReq req) {
        return Result.ok(teacherService.autoAdmit(userId,
                req.round == null ? 1 : req.round, req.sortBy));
    }

    // ---------------- 内部调剂（我作为接收方） ----------------

    /** 别人指定调剂给我的学生 */
    @GetMapping("/adjust-inbox")
    public Result<List<com.college.selection.entity.Offer>> adjustInbox(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(teacherService.adjustInbox(userId, round));
    }

    /** 接收调剂：直接变成我的拟录取（不再二次面试） */
    @PostMapping("/adjust-inbox/{offerId}/accept")
    public Result<Map<String, Object>> acceptAdjust(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long offerId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(teacherService.acceptAdjust(userId, round, offerId));
    }

    /** 拒绝调剂：退回发起人 */
    @PostMapping("/adjust-inbox/{offerId}/reject")
    public Result<Map<String, Object>> rejectAdjust(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long offerId,
            @RequestParam(defaultValue = "1") Integer round,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return Result.ok(teacherService.rejectAdjust(userId, round, offerId, reason));
    }
}
