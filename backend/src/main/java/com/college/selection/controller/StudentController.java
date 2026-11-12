package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.config.LoginInterceptor;
import com.college.selection.dto.ApplicationReq;
import com.college.selection.dto.StudentStatusVO;
import com.college.selection.entity.Application;
import com.college.selection.entity.Offer;
import com.college.selection.service.ApplicationService;
import com.college.selection.service.OfferService;
import com.college.selection.service.StudentStatusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 学生端：填报、查看状态、看意向、确认去向 */
@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final ApplicationService applicationService;
    private final OfferService offerService;
    private final StudentStatusService statusService;

    public StudentController(ApplicationService applicationService,
                             OfferService offerService,
                             StudentStatusService statusService) {
        this.applicationService = applicationService;
        this.offerService = offerService;
        this.statusService = statusService;
    }

    /** 本人某轮意向，未填报返回 data: null */
    @GetMapping("/application")
    public Result<Application> getApplication(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(applicationService.get(userId, round));
    }

    /** 提交 / 修改意向 */
    @PutMapping("/application")
    public Result<Void> saveApplication(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @Valid @RequestBody ApplicationReq req) {
        applicationService.save(userId, req);
        return Result.ok(null);
    }

    /** 我的选拔状态 */
    @GetMapping("/status")
    public Result<StudentStatusVO> status(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(statusService.compute(userId, round));
    }

    /** 本人收到的全部意向（拟录取 / 递补 / 调剂候选） */
    @GetMapping("/offers")
    public Result<List<Offer>> offers(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(offerService.list(userId, round));
    }

    /** 确认唯一去向；其余意向自动置为放弃 */
    @PostMapping("/offers/{offerId}/confirm")
    public Result<Map<String, Object>> confirm(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long offerId) {
        return Result.ok(offerService.confirm(userId, offerId));
    }

    /** 最终去向 */
    @GetMapping("/result")
    public Result<Offer> result(
            @RequestAttribute(LoginInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(offerService.result(userId, round));
    }
}
