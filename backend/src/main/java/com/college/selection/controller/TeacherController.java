package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.config.LoginInterceptor;
import com.college.selection.dto.ApplicantVO;
import com.college.selection.dto.ScoreReq;
import com.college.selection.dto.TeacherListsReq;
import com.college.selection.dto.TeacherListsVO;
import com.college.selection.service.TeacherService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
