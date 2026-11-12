package com.college.selection.service;

import com.college.selection.dto.StudentStatusVO;
import com.college.selection.entity.Application;
import com.college.selection.entity.Offer;
import com.college.selection.entity.Publicity;
import com.college.selection.entity.Stage;
import com.college.selection.mapper.AdminMapper;
import com.college.selection.mapper.ApplicationMapper;
import com.college.selection.mapper.OfferMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学生状态推导。
 *
 * 状态不建表，由 application + offer + publicity 推导（见 schema.sql 末尾的枚举说明），
 * 而且只在 service 层算——controller 不允许自己拼状态。
 *
 * 注：这里注入 AdminMapper 只是为了读一下 latestPublicity（公示是否结束），
 * 因为"正式入组"这个状态依赖公示结果。
 */
@Service
public class StudentStatusService {

    private final ApplicationMapper appMapper;
    private final OfferMapper offerMapper;
    private final StageService stageService;
    private final AdminMapper adminMapper;

    public StudentStatusService(ApplicationMapper appMapper,
                                OfferMapper offerMapper,
                                StageService stageService,
                                AdminMapper adminMapper) {
        this.appMapper = appMapper;
        this.offerMapper = offerMapper;
        this.stageService = stageService;
        this.adminMapper = adminMapper;
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

        boolean confirmed = offers.stream().anyMatch(o -> "CONFIRMED".equals(o.status));
        boolean admittedIssued = offers.stream()
                .anyMatch(o -> "ADMITTED".equals(o.type) && "ISSUED".equals(o.status));
        boolean waitlist = offers.stream()
                .anyMatch(o -> "WAITLIST".equals(o.type) && "ISSUED".equals(o.status));
        boolean adjustCandidate = offers.stream()
                .anyMatch(o -> "ADJUST_CANDIDATE".equals(o.type));

        Publicity pub = adminMapper.latestPublicity(round);
        boolean publicityEnded = pub != null && "ENDED".equals(pub.status);
        boolean publicityOpen = pub != null && "PUBLISHED".equals(pub.status);
        vo.publicity = publicityEnded ? "ENDED" : publicityOpen ? "PUBLISHED" : "NONE";

        // 本轮没有确认记录时，回看前面几轮：录取是终局状态，后续轮次不再填报
        Offer earlier = confirmed ? null : offerMapper.findConfirmedUpTo(studentId, round);

        if (confirmed && publicityEnded) {
            set(vo, "ADMITTED", "已正式入组");
            vo.admittedRound = round;
        } else if (confirmed) {
            set(vo, "CONFIRMED", publicityOpen ? "已确认去向，公示中" : "已确认去向");
            vo.admittedRound = round;
        } else if (earlier != null) {
            Publicity ep = adminMapper.latestPublicity(earlier.roundNo);
            boolean earlierEnded = ep != null && "ENDED".equals(ep.status);
            boolean earlierOpen = ep != null && "PUBLISHED".equals(ep.status);
            vo.admittedRound = earlier.roundNo;
            vo.admittedLabName = earlier.labName;
            set(vo, earlierEnded ? "ADMITTED" : "CONFIRMED",
                    "已在第 " + earlier.roundNo + " 轮被录取（" + earlier.labName + "）"
                            + (earlierEnded ? "，公示已结束" : earlierOpen ? "，公示中" : "，待公示")
                            + "，无需再填报");
        } else if (admittedIssued) {
            set(vo, "OFFERED", "已获得拟录取，请在截止前选择唯一去向");
        } else if (waitlist) {
            set(vo, "WAITLISTED", "递补中，等待名额空出");
        } else if (adjustCandidate) {
            set(vo, "ADJUSTING", "已进入调剂候选，等待统筹安排");
        } else if (app == null) {
            set(vo, "NOT_APPLIED", "尚未填报意向");
        } else if (cur == null) {
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
