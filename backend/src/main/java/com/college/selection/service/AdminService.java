package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.dto.AdjustPoolVO;
import com.college.selection.dto.QuotaVO;
import com.college.selection.entity.Mentor;
import com.college.selection.entity.Offer;
import com.college.selection.entity.Publicity;
import com.college.selection.entity.Stage;
import com.college.selection.entity.SysUser;
import com.college.selection.mapper.AdminMapper;
import com.college.selection.mapper.MentorMapper;
import com.college.selection.mapper.OfferMapper;
import com.college.selection.mapper.OperationLogMapper;
import com.college.selection.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    /** 统筹调剂对应流程图第 7 个环节 */
    private static final int STAGE_ADJUST = 7;
    /** 拟录取与学生确认对应第 5 个环节 */
    private static final int STAGE_CONFIRM = 5;

    private final AdminMapper adminMapper;
    private final OfferMapper offerMapper;
    private final MentorMapper mentorMapper;
    private final SysUserMapper userMapper;
    private final StageService stageService;
    private final OperationLogMapper logMapper;

    public AdminService(AdminMapper adminMapper,
                        OfferMapper offerMapper,
                        MentorMapper mentorMapper,
                        SysUserMapper userMapper,
                        StageService stageService,
                        OperationLogMapper logMapper) {
        this.adminMapper = adminMapper;
        this.offerMapper = offerMapper;
        this.mentorMapper = mentorMapper;
        this.userMapper = userMapper;
        this.stageService = stageService;
        this.logMapper = logMapper;
    }

    private SysUser requireAdmin(Long adminId) {
        SysUser u = userMapper.findById(adminId);
        if (u == null || !"ADMIN".equals(u.role)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有学院管理员可以执行该操作");
        }
        return u;
    }

    // ---------------- 名额总览 ----------------

    public List<QuotaVO> quotas(Integer round) {
        List<QuotaVO> list = adminMapper.quotas(round);
        for (QuotaVO q : list) {
            q.remaining = (q.quota == null ? 0 : q.quota) - (q.confirmed == null ? 0 : q.confirmed);
        }
        return list;
    }

    /**
     * 公布剩余名额。
     * 真正做的事：把当前剩余名额汇总出来 + 写一条操作日志（留痕），
     * 前端拿到总数后提示学生。补录本身仍然靠"开启下一轮"。
     */
    @Transactional
    public Map<String, Object> publishQuotas(Long adminId, Integer round) {
        requireAdmin(adminId);
        List<QuotaVO> qs = quotas(round);
        int total = 0;
        for (QuotaVO q : qs) {
            total += (q.remaining == null ? 0 : q.remaining);
        }
        String remark = String.format("公布第 %d 轮剩余名额，共 %d 个", round, total);
        logMapper.insert(adminId, "ADMIN", "PUBLISH_QUOTA", "ROUND", round.longValue(), remark);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("round", round);
        m.put("total", total);
        m.put("labs", qs);
        return m;
    }

    // ---------------- 调剂池 ----------------
    public AdjustPoolVO pool(Integer round) {
        AdjustPoolVO vo = new AdjustPoolVO();
        vo.round = round;
        vo.vacancies = adminMapper.vacancies(round);
        for (AdjustPoolVO.Vacancy v : vo.vacancies) {
            v.remaining = (v.quota == null ? 0 : v.quota) - (v.confirmed == null ? 0 : v.confirmed);
        }
        vo.candidates = adminMapper.candidates(round);
        return vo;
    }

    /**
     * 第一优先级：实训室内部调剂。
     * 把学生安排到本室的某个导师，直接置为已确认（统筹调剂不需要学生再确认）。
     */
    @Transactional
    public void adjustInternal(Long adminId, Integer round, Long mentorId, List<Long> studentIds) {
        requireAdmin(adminId);
        stageService.assertOpen(round, STAGE_ADJUST);
        place(round, mentorId, studentIds, "统筹调剂-室内调剂");
    }

    /**
     * 第二优先级：全院跨室调剂。
     * 必须先完成第一优先级（还有答应室内调剂的学生没着落时不允许进这一级）。
     */
    @Transactional
    public void adjustCross(Long adminId, Integer round, Long mentorId, List<Long> studentIds) {
        requireAdmin(adminId);
        stageService.assertOpen(round, STAGE_ADJUST);
        int pending = adminMapper.countPendingInternal(round);
        if (pending > 0) {
            throw new BizException(ErrorCode.ADJUST_ORDER_WRONG,
                    "还有 " + pending + " 名服从室内调剂的学生没有着落，不能跳过第一优先级直接做跨室调剂");
        }
        place(round, mentorId, studentIds, "统筹调剂-跨室调剂");
    }

    /**
     * 第三优先级：约谈未服从调剂的学生并作最终安置。
     * 必须前两级都已完成。
     */
    @Transactional
    public void adjustTalk(Long adminId, Integer round, Long mentorId, Long studentId, String conclusion) {
        requireAdmin(adminId);
        stageService.assertOpen(round, STAGE_ADJUST);
        int pending = adminMapper.countPendingCross(round);
        if (pending > 0) {
            throw new BizException(ErrorCode.ADJUST_ORDER_WRONG,
                    "还有 " + pending + " 名服从跨室调剂的学生没有着落，不能跳过第二优先级直接做约谈安置");
        }
        if (conclusion == null || conclusion.isBlank()) {
            throw new BizException(2008, "约谈安置必须填写约谈结论");
        }
        place(round, mentorId, List.of(studentId), "统筹调剂-约谈：" + conclusion);
    }

    /** 把一个或多个学生直接安排给某个导师 */
    private void place(Integer round, Long mentorId, List<Long> studentIds, String remark) {
        if (mentorId == null || studentIds == null || studentIds.isEmpty()) {
            throw new BizException(2008, "必须指定导师和学生");
        }
        Mentor mentor = mentorMapper.findById(mentorId);
        if (mentor == null) {
            throw new BizException(2008, "导师不存在：" + mentorId);
        }

        // 锁住导师行，防止并发把名额超卖
        Integer quota = offerMapper.lockMentorQuota(mentorId);
        int confirmed = offerMapper.countConfirmed(mentorId, round);
        if (quota == null || confirmed + studentIds.size() > quota) {
            throw new BizException(ErrorCode.QUOTA_FULL,
                    String.format("%s 名额不足：剩余 %d，本次安排 %d 人",
                            mentor.name, Math.max(0, (quota == null ? 0 : quota) - confirmed), studentIds.size()));
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long studentId : studentIds) {
            if (offerMapper.countAnyConfirmed(studentId, round) > 0) {
                throw new BizException(2008, "学生 " + studentId + " 已经确认了去向");
            }
            // 同一 (学生, 导师, 轮次) 只能有一条 offer，先清掉旧的
            offerMapper.deleteByStudentMentor(studentId, mentorId, round);

            Offer o = new Offer();
            o.studentId = studentId;
            o.labId = mentor.labId;
            o.mentorId = mentorId;
            o.roundNo = round;
            o.type = "ADMITTED";
            o.rankNo = null;
            o.status = "ISSUED";
            o.issuedAt = now;
            o.confirmDeadline = now;
            o.remark = remark;
            offerMapper.insert(o);
            offerMapper.updateStatus(o.id, "CONFIRMED", now);
            // 其他意向全部置为放弃，保证"一人一个去向"
            offerMapper.declineOthers(studentId, round, o.id);
        }
    }

    // ---------------- 阶段 ----------------

    public List<Stage> stages(Integer round) {
        return adminMapper.allStages(round);
    }

    public void updateStage(Long adminId, Long stageId, LocalDateTime startAt, LocalDateTime endAt) {
        requireAdmin(adminId);
        if (startAt == null || endAt == null) {
            throw new BizException(2009, "起止时间都要填");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BizException(2009, "结束时间必须晚于开始时间");
        }
        adminMapper.updateStage(stageId, startAt, endAt);
    }

    /**
     * 切换当前阶段。
     *
     * 必须"先关掉本轮其他阶段，再打开目标阶段"，否则会出现多个阶段同时开放，
     * 导致 currentStage 语义模糊（管理员以为切到了阶段 5，学生端还认为在阶段 3 能填报）。
     */
    @Transactional
    public Map<String, Object> switchStage(Long adminId, Integer round, Integer stageNo, Integer hours) {
        requireAdmin(adminId);
        if (stageNo == null) {
            throw new BizException(2009, "必须指定要切换到的阶段");
        }
        int h = (hours == null || hours < 1) ? 4 : hours;
        LocalDateTime now = LocalDateTime.now();
        adminMapper.closeAllStages(round, now);
        adminMapper.openStage(round, stageNo, now.minusHours(1), now.plusHours(h));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stageNo", stageNo);
        // 进入"拟录取与学生确认"时统一结算：1 个意向自动确认，多个等学生自己选
        if (stageNo == STAGE_CONFIRM) {
            m.putAll(settleOffers(round));
        }
        return m;
    }

    /**
     * 面试结束后统一结算。
     *
     * 为什么不放在导师提交名单时：导师是并行提交的，
     * 如果一发就自动确认，第一个发名单的导师会把学生锁死，
     * "收到 2 个以上意向由学生自主选择"就永远不会发生。
     * 所以放到进入确认阶段时统一处理。
     */
    @Transactional
    public Map<String, Object> settle(Long adminId, Integer round) {
        requireAdmin(adminId);
        return settleOffers(round);
    }

    private Map<String, Object> settleOffers(Integer round) {
        // 先按阶段 5 的真实窗口刷新确认截止时间（发名单时阶段 5 可能还没配置）
        LocalDateTime confirmEnd = stageService.getEndAt(round, STAGE_CONFIRM);
        if (confirmEnd != null) {
            offerMapper.refreshIssuedDeadline(round, confirmEnd);
        }

        List<Long> sids = adminMapper.studentsWithIssuedAdmitted(round);
        int auto = 0, multi = 0;
        for (Long sid : sids) {
            int cnt = offerMapper.countAdmittedIssued(sid, round);
            if (cnt == 1) {
                offerMapper.autoConfirmByStudent(sid, round);
                auto++;
            } else if (cnt >= 2) {
                multi++;
            }
        }
        String remark = String.format("结算第 %d 轮：自动确认 %d 人，多意向待学生选择 %d 人", round, auto, multi);
        logMapper.insert(null, "SYSTEM", "SETTLE_OFFERS", "ROUND", round.longValue(), remark);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("autoConfirmed", auto);
        m.put("multiChoice", multi);
        return m;
    }

    // ---------------- 公示 ----------------

    public Publicity latestPublicity(Integer round) {
        return adminMapper.latestPublicity(round);
    }

    public Publicity publish(Long adminId, Integer round, Integer days) {
        requireAdmin(adminId);
        Publicity last = adminMapper.latestPublicity(round);
        if (last != null && "PUBLISHED".equals(last.status)) {
            throw new BizException(2010, "已经处于公示中，请先结束本次公示");
        }
        int d = (days == null || days < 1) ? 1 : days;
        LocalDateTime now = LocalDateTime.now();

        Publicity p = new Publicity();
        p.roundNo = round;
        p.status = "PUBLISHED";
        p.startAt = now;
        p.endAt = now.plusDays(d);
        p.objectionCount = 0;
        p.publishedBy = adminId;
        p.publishedAt = now;
        adminMapper.insertPublicity(p);
        return p;
    }

    public void endPublicity(Long adminId, Integer round) {
        requireAdmin(adminId);
        Publicity last = adminMapper.latestPublicity(round);
        if (last == null || !"PUBLISHED".equals(last.status)) {
            throw new BizException(2010, "当前没有进行中的公示");
        }
        if (last.objectionCount != null && last.objectionCount > 0) {
            throw new BizException(ErrorCode.PENDING_OBJECTION,
                    "还有 " + last.objectionCount + " 条异议未处理，不能结束公示");
        }
        adminMapper.updatePublicity(last.id, "ENDED", LocalDateTime.now());
    }

    // ---------------- 补录轮次 ----------------

    /**
     * 开启下一轮补录。
     * 会把上一轮置为 CLOSED，并把上一轮的阶段时间窗复制一份给新一轮
     * （管理员随后在 /admin/stages 里改成本轮的真实时间）。
     */
    @Transactional
    public void openRound(Long adminId, Integer round) {
        requireAdmin(adminId);
        if (round == null || round < 2 || round > 3) {
            throw new BizException(2011, "只能开启第 2 轮或第 3 轮补录");
        }
        String prevStatus = adminMapper.roundStatus(round - 1);
        if (prevStatus == null) {
            throw new BizException(2011, "第 " + (round - 1) + " 轮不存在");
        }
        if ("OPEN".equals(prevStatus)) {
            adminMapper.closeRound(round - 1);
        }
        if (adminMapper.countStages(round) == 0) {
            adminMapper.copyStages(round, round - 1);
        }
        adminMapper.openRound(round);
    }

    // ---------------- 公示异议 ----------------

    public void addObjection(Long adminId, Integer round, String reason) {
        requireAdmin(adminId);
        Publicity p = adminMapper.latestPublicity(round);
        if (p == null || !"PUBLISHED".equals(p.status)) {
            throw new BizException(2010, "当前没有进行中的公示");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(2012, "异议内容不能为空");
        }
        adminMapper.addObjection(p.id);
    }

    /** 处理完异议后清零，清零之后才允许结束公示 */
    public void resolveObjections(Long adminId, Integer round) {
        requireAdmin(adminId);
        Publicity p = adminMapper.latestPublicity(round);
        if (p == null) {
            throw new BizException(2010, "还没有公示记录");
        }
        adminMapper.clearObjections(p.id);
    }

    // ---------------- 学生名单 ----------------

    /** 管理端用：全部学生，前端看板和数据表格要 */
    public List<Map<String, Object>> allStudents() {
        return adminMapper.allStudents();
    }

    /** 已确认的最终名单（公示用） */
    public List<Map<String, Object>> confirmedResults(Integer round) {
        return adminMapper.confirmedResults(round);
    }

    // ---------------- 看板 ----------------

    public Map<String, Object> dashboard(Integer round) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applied", adminMapper.countAppliedStudents(round));
        m.put("interviewed", adminMapper.countInterviewed(round));
        m.put("issued", adminMapper.countIssued(round));
        m.put("confirmed", adminMapper.countConfirmedOffers(round));
        m.put("waitlist", adminMapper.countWaitlist(round));
        m.put("adjustCandidates", adminMapper.countAdjustCandidates(round));
        m.put("pendingInternal", adminMapper.countPendingInternal(round));
        m.put("pendingCross", adminMapper.countPendingCross(round));
        return m;
    }
}
