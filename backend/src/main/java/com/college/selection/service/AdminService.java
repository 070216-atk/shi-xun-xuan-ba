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
import com.college.selection.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final AdminMapper adminMapper;
    private final OfferMapper offerMapper;
    private final MentorMapper mentorMapper;
    private final SysUserMapper userMapper;

    public AdminService(AdminMapper adminMapper,
                        OfferMapper offerMapper,
                        MentorMapper mentorMapper,
                        SysUserMapper userMapper) {
        this.adminMapper = adminMapper;
        this.offerMapper = offerMapper;
        this.mentorMapper = mentorMapper;
        this.userMapper = userMapper;
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
        place(round, mentorId, studentIds, "统筹调剂-室内调剂");
    }

    /**
     * 第二优先级：全院跨室调剂。
     * 必须先完成第一优先级（还有答应室内调剂的学生没着落时不允许进这一级）。
     */
    @Transactional
    public void adjustCross(Long adminId, Integer round, Long mentorId, List<Long> studentIds) {
        requireAdmin(adminId);
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
