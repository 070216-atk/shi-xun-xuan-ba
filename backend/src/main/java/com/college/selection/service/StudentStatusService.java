package com.college.selection.service;

import com.college.selection.dto.StudentStatusVO;
import com.college.selection.entity.Application;
import com.college.selection.entity.Offer;
import com.college.selection.entity.Stage;
import com.college.selection.mapper.ApplicationMapper;
import com.college.selection.mapper.OfferMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学生状态推导。
 *
 * 状态不建表，由 application + offer 推导（见 schema.sql 末尾的枚举说明），
 * 而且只在 service 层算——controller 不允许自己拼状态。
 */
@Service
public class StudentStatusService {

    private final ApplicationMapper appMapper;
    private final OfferMapper offerMapper;
    private final StageService stageService;

    public StudentStatusService(ApplicationMapper appMapper,
                                OfferMapper offerMapper,
                                StageService stageService) {
        this.appMapper = appMapper;
        this.offerMapper = offerMapper;
        this.stageService = stageService;
    }

    public StudentStatusVO compute(Long studentId, Integer round) {
        StudentStatusVO vo = new StudentStatusVO();
        vo.round = round;

        Stage cur = stageService.current(round);
        if (cur != null) {
            vo.currentStage = cur.stageNo;
            vo.currentStageName = cur.name;
            vo.deadline = cur.endAt;
            vo.remainingSeconds = cur.remainingSeconds;
        }

        Application app = appMapper.find(studentId, round);
        List<Offer> offers = offerMapper.findByStudent(studentId, round);
        vo.offerCount = offers.size();

        boolean confirmed = offers.stream()
                .anyMatch(o -> "CONFIRMED".equals(o.status));
        boolean admittedIssued = offers.stream()
                .anyMatch(o -> "ADMITTED".equals(o.type) && "ISSUED".equals(o.status));
        boolean waitlist = offers.stream()
                .anyMatch(o -> "WAITLIST".equals(o.type) && "ISSUED".equals(o.status));
        boolean adjustCandidate = offers.stream()
                .anyMatch(o -> "ADJUST_CANDIDATE".equals(o.type));

        if (confirmed) {
            set(vo, "CONFIRMED", "已确认去向");
        } else if (admittedIssued) {
            set(vo, "OFFERED", "已获得拟录取，请在截止前选择唯一去向");
        } else if (waitlist) {
            set(vo, "WAITLISTED", "递补中，等待名额空出");
        } else if (adjustCandidate) {
            set(vo, "ADJUSTING", "已进入调剂候选，等待统筹安排");
        } else if (app == null) {
            // 一次都没填报过，和"填了但没录取"是两种状态，不能混
            set(vo, "NOT_APPLIED", "尚未填报意向");
        } else if (cur == null) {
            // 填过、当前没有任何阶段在开放、也没拿到意向
            set(vo, "NOT_ADMITTED", "本轮暂无录取结果");
        } else {
            set(vo, "APPLIED", "已提交意向，等待面试");
        }
        return vo;
    }

    private void set(StudentStatusVO vo, String status, String text) {
        vo.status = status;
        vo.statusText = text;
    }
}
