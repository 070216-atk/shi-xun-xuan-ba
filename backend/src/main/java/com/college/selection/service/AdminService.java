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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    /** 统筹调剂对应流程图第 7 个环节 */
    private static final int STAGE_ADJUST = 7;
    /** 分轮补录对应第 6 个环节（补录轮开启后直接进入报名） */
    private static final int STAGE_REFILL = 6;
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
        // 必须走 StageService 才能算出 open / isCurrent，否则管理端所有阶段都显示"未开放"
        return stageService.list(round);
    }

    public void updateStage(Long adminId, Long stageId, LocalDateTime startAt, LocalDateTime endAt) {
        requireAdmin(adminId);
        if (startAt == null || endAt == null) {
            throw new BizException(2009, "起止时间都要填");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BizException(2009, "结束时间必须晚于开始时间");
        }
        Stage target = adminMapper.stageById(stageId);
        if (target == null) {
            throw new BizException(2009, "阶段不存在");
        }
        // 同一时间只允许一个阶段开放，避免学生端/导师端对"当前阶段"判断不一致
        List<Stage> conflicts = adminMapper.overlappingStages(target.roundNo, stageId, startAt, endAt);
        if (!conflicts.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (Stage s : conflicts) {
                if (names.length() > 0) names.append("、");
                names.append("「").append(s.name).append("」");
            }
            throw new BizException(2009, "该时间与" + names + "重叠；同一时间只能开放一个阶段");
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
        // 允许自由切换（含切回更早的阶段）：演示、纠错、重跑流程都需要。
        // 切阶段只改时间窗，不会删除任何已产生的数据；下游操作各自有状态校验兜底。
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
        int auto = 0, multi = 0, skipped = 0;
        for (Long sid : sids) {
            int cnt = offerMapper.countAdmittedIssued(sid, round);
            if (cnt == 1) {
                // 只有一个意向也要先看名额：名额可能被改小过，不能盲目确认
                List<Offer> issued = offerMapper.findIssuedAdmitted(sid, round);
                Offer pick = issued.isEmpty() ? null : issued.get(0);
                boolean roomLeft = false;
                if (pick != null) {
                    Integer quota = offerMapper.lockMentorQuota(pick.mentorId);
                    int used = offerMapper.countConfirmed(pick.mentorId, round);
                    roomLeft = quota != null && used < quota;
                }
                if (roomLeft) {
                    offerMapper.autoConfirmByStudent(sid, round);
                    auto++;
                } else {
                    skipped++;   // 名额已满，先不确认，留给收口重新分配
                }
            } else if (cnt >= 2) {
                multi++;
            }
        }
        String remark = String.format("结算第 %d 轮：自动确认 %d 人，多意向待学生选择 %d 人，因名额已满暂缓 %d 人",
                round, auto, multi, skipped);
        logMapper.insert(null, "SYSTEM", "SETTLE_OFFERS", "ROUND", round.longValue(), remark);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("autoConfirmed", auto);
        m.put("multiChoice", multi);
        m.put("skippedQuotaFull", skipped);
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

        // 公示前必须收口：每个人都要有且只有一个实训室，导师不能超录
        Map<String, Object> verify = placementVerify(round);
        if (!Boolean.TRUE.equals(verify.get("ok"))) {
            int missing = ((List<?>) verify.get("missing")).size();
            int dup = ((List<?>) verify.get("duplicate")).size();
            int over = ((List<?>) verify.get("overQuota")).size();
            throw new BizException(2010,
                    String.format("还不能公示：还有 %d 人没有实训室、%d 人有重复去向、%d 位导师超录。请先在「收口安置」里处理后重试",
                            missing, dup, over));
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
        // 补录轮的核心就是让学生重新报名：直接打开「分轮补录」窗口，
        // 否则复制过来的阶段时间窗都是上一轮的过去时间，学生端无法填报
        LocalDateTime now = LocalDateTime.now();
        adminMapper.closeAllStages(round, now);
        adminMapper.openStage(round, STAGE_REFILL, now.minusHours(1), now.plusHours(4));
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

    // ---------------- 收口安置（确保每个人都有实训室） ----------------

    private boolean asBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return "true".equalsIgnoreCase(v.toString()) || "1".equals(v.toString());
    }

    private Map<Long, Integer> capacityLeft(Integer round) {
        Map<Long, Integer> left = new LinkedHashMap<>();
        for (Map<String, Object> c : adminMapper.mentorCapacity(round)) {
            long mid = ((Number) c.get("mentorId")).longValue();
            int quota = ((Number) c.get("quota")).intValue();
            int confirmed = ((Number) c.get("confirmed")).intValue();
            left.put(mid, Math.max(0, quota - confirmed));
        }
        return left;
    }

    private Map<Long, Map<String, Object>> studentInfo() {
        Map<Long, Map<String, Object>> info = new LinkedHashMap<>();
        for (Map<String, Object> s : adminMapper.allStudents()) {
            info.put(((Number) s.get("studentId")).longValue(), s);
        }
        return info;
    }

    /** 挑一个还有空位的导师；onlyLabs=true 时只在指定实训室里挑 */
    private Long pickMentor(Map<Long, Integer> left, List<Map<String, Object>> caps,
                            java.util.Set<Long> labs, boolean onlyLabs) {
        for (Map<String, Object> c : caps) {
            long mid = ((Number) c.get("mentorId")).longValue();
            long labId = ((Number) c.get("labId")).longValue();
            if (left.getOrDefault(mid, 0) <= 0) continue;
            if (onlyLabs && (labs == null || !labs.contains(labId))) continue;
            return mid;
        }
        return null;
    }

    /** 直接把某个学生落到某个导师名下（已有意向就改状态，没有就新建） */
    private void placeConfirmed(Long studentId, Long mentorId, Long labId, Integer round,
                                LocalDateTime now, String remark) {
        for (Offer o : offerMapper.findByStudent(studentId, round)) {
            if (o.mentorId.equals(mentorId)
                    && !"VOID".equals(o.status) && !"DECLINED".equals(o.status)) {
                offerMapper.updateStatus(o.id, "CONFIRMED", now);
                offerMapper.expireAllOther(studentId, round, o.id);
                return;
            }
        }
        Offer o = new Offer();
        o.studentId = studentId;
        o.labId = labId;
        o.mentorId = mentorId;
        o.roundNo = round;
        o.type = "ADMITTED";
        o.rankNo = offerMapper.maxAdmittedRank(mentorId, round) + 1;
        o.status = "ISSUED";
        o.issuedAt = now;
        o.remark = remark;
        offerMapper.insert(o);
        offerMapper.updateStatus(o.id, "CONFIRMED", now);
        offerMapper.expireAllOther(studentId, round, o.id);
    }

    /**
     * 管理员指定安置（约谈后的"同意的去处"）。
     *
     * 和批量收口的区别：这里是管理员明确指定去哪个导师，并且必须写原因，
     * 结果会直接变成"已确认"，备注写明"学院统筹安置"，学生在自己页面能看到这条说明。
     */
    @Transactional
    public Map<String, Object> assignPlacement(Long adminId, Integer round, Long studentId,
                                               Long mentorId, String reason) {
        requireAdmin(adminId);
        Publicity pub = adminMapper.latestPublicity(round);
        if (pub != null && ("PUBLISHED".equals(pub.status) || "ENDED".equals(pub.status))) {
            throw new BizException(2010, "最终名单已经公示，不能再改安置结果；如需调整请先作废公示");
        }

        if (studentId == null || mentorId == null) {
            throw new BizException(2007, "必须同时指定学生和导师");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(2007, "必须填写安置原因（例如约谈结论），否则学生看不到为什么被安排到这里");
        }
        if (!adminMapper.appliedStudentIds(round).contains(studentId)) {
            // 补录全部结束后，没报过名 / 只在往轮报过名的未录取学生也允许管理员统筹安置
            if (!registrationClosed(round)) {
                throw new BizException(2007, "该学生本轮没有填报，且报名通道还没关闭，暂不能统筹安置");
            }
            if (adminMapper.countUnplacedStudent(studentId) == 0) {
                throw new BizException(2007, "该学生已经有确认去向，不能重复安置");
            }
        }

        Mentor m = mentorMapper.findById(mentorId);
        if (m == null) {
            throw new BizException(2007, "导师不存在：" + mentorId);
        }
        Integer quota = offerMapper.lockMentorQuota(mentorId);
        if (quota == null) {
            throw new BizException(2007, "导师档案异常");
        }

        // 如果该学生已经确认在这里，直接返回
        for (Offer o : offerMapper.findByStudent(studentId, round)) {
            if (o.mentorId.equals(mentorId) && "CONFIRMED".equals(o.status)) {
                Map<String, Object> same = new LinkedHashMap<>();
                same.put("studentId", studentId);
                same.put("mentorId", mentorId);
                same.put("already", true);
                return same;
            }
        }

        // 目标导师已满 → 拒绝（先把原记录留着，不动）
        int confirmedCount = offerMapper.countConfirmed(mentorId, round);
        if (confirmedCount >= quota) {
            throw new BizException(ErrorCode.QUOTA_FULL,
                    String.format("%s 已录满（%d/%d），请换一个导师", m.name, confirmedCount, quota));
        }

        String remark = "学院统筹安置：" + reason.trim();
        LocalDateTime now = LocalDateTime.now();

        // 已有与该导师的有效意向 → 直接确认；否则新建
        Offer target = null;
        for (Offer o : offerMapper.findByStudent(studentId, round)) {
            if (o.mentorId.equals(mentorId)
                    && !"VOID".equals(o.status) && !"DECLINED".equals(o.status)) {
                target = o;
                break;
            }
        }
        if (target != null) {
            offerMapper.updateStatus(target.id, "CONFIRMED", now);
        } else {
            Offer o = new Offer();
            o.studentId = studentId;
            o.labId = m.labId;
            o.mentorId = mentorId;
            o.roundNo = round;
            o.type = "ADMITTED";
            o.rankNo = offerMapper.maxAdmittedRank(mentorId, round) + 1;
            o.status = "ISSUED";
            o.issuedAt = now;
            o.remark = remark;
            offerMapper.insert(o);
            offerMapper.updateStatus(o.id, "CONFIRMED", now);
            target = o;
        }
        offerMapper.setRemark(target.id, remark);

        // 原来别处的确认（改派）与其余待确认意向一并作废
        List<Offer> before = offerMapper.findByStudent(studentId, round);
        for (Offer o : before) {
            if (!o.mentorId.equals(mentorId) && "CONFIRMED".equals(o.status)) {
                offerMapper.updateStatus(o.id, "EXPIRED", null);
                promoteNextWaitlist(o.mentorId, round);
            }
        }
        offerMapper.expireOtherConfirmed(studentId, round, target.id);
        offerMapper.expireAllOther(studentId, round, target.id);

        logMapper.insertWithState(adminId, "ADMIN", "ASSIGN_PLACEMENT", "OFFER", target.id,
                null, "CONFIRMED",
                "学院统筹安置 " + who(studentId) + " → " + m.name + "，原因：" + reason.trim());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentId", studentId);
        data.put("mentorId", mentorId);
        data.put("mentorName", m.name);
        data.put("labId", m.labId);
        data.put("remark", remark);
        data.put("already", false);
        return data;
    }

    /** 把学生 id 换成人看得懂的名字 */
    private String who(Long studentId) {
        var u = userMapper.findById(studentId);
        return u == null ? ("学生 #" + studentId) : (u.realName + "（" + u.code + "）");
    }
    /** 名额腾出来后让该导师递补第一名上位 */
    private void promoteNextWaitlist(Long mentorId, Integer round) {
        List<Offer> wl = offerMapper.findWaitlist(mentorId, round);
        if (wl.isEmpty()) return;
        Offer next = wl.get(0);
        offerMapper.promoteToAdmitted(next.id,
                offerMapper.maxAdmittedRank(mentorId, round) + 1,
                stageService.getEndAt(round, STAGE_CONFIRM));
    }

    /** 收口前的缺口清单：谁还没着落、还有多少空位、够不够 */
    public Map<String, Object> placementGaps(Integer round) {
        List<Long> applied = adminMapper.appliedStudentIds(round);
        // 跨轮判定：后面某一轮已经被录取的人，本轮不再算未安置
        java.util.Set<Long> confirmed = new java.util.HashSet<>(adminMapper.confirmedFromRoundOn(round));
        Map<Long, Integer> left = capacityLeft(round);
        Map<Long, Map<String, Object>> info = studentInfo();

        int totalLeft = 0;
        for (int v : left.values()) totalLeft += v;

        List<Map<String, Object>> unplaced = new ArrayList<>();
        java.util.Set<Long> listed = new java.util.HashSet<>();
        int appliedUnplaced = 0;
        for (Long sid : applied) {
            if (confirmed.contains(sid)) continue;
            unplaced.add(unplacedItem(info, sid, "ROUND"));
            listed.add(sid);
            appliedUnplaced++;
        }

        // 最后一轮的报名通道彻底关闭后，所有还没着落的学生都进入统筹安置：
        // 从头没报过名的（NEVER）和只在往轮报过名、一直没录取的（EARLIER）
        int neverApplied = 0, earlierOnly = 0;
        if (registrationClosed(round)) {
            for (Map<String, Object> s : adminMapper.studentsWithoutPlacement()) {
                long sid = ((Number) s.get("studentId")).longValue();
                if (listed.contains(sid)) continue;
                boolean never = ((Number) s.get("applicationCount")).intValue() == 0;
                unplaced.add(unplacedItem(info, sid, never ? "NEVER" : "EARLIER"));
                if (never) neverApplied++; else earlierOnly++;
            }
        }

        List<Map<String, Object>> available = new ArrayList<>();
        for (Map<String, Object> c : adminMapper.mentorCapacity(round)) {
            long mid = ((Number) c.get("mentorId")).longValue();
            int rem = left.getOrDefault(mid, 0);
            if (rem <= 0) continue;
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("mentorId", mid);
            a.put("labId", c.get("labId"));
            a.put("labCode", c.get("labCode"));
            a.put("labName", c.get("labName"));
            a.put("mentorName", c.get("mentorName"));
            a.put("remaining", rem);
            available.add(a);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("round", round);
        m.put("appliedCount", applied.size());
        m.put("confirmedCount", applied.size() - appliedUnplaced);
        m.put("unplacedCount", unplaced.size());
        m.put("neverAppliedCount", neverApplied);
        m.put("earlierOnlyCount", earlierOnly);
        m.put("totalRemaining", totalLeft);
        m.put("feasible", totalLeft >= unplaced.size());
        int lastRound = adminMapper.lastActiveRound();
        m.put("lastRound", lastRound);
        m.put("finalRound", round >= lastRound);
        m.put("availableMentors", available);
        m.put("unplaced", unplaced);
        return m;
    }

    private Map<String, Object> unplacedItem(Map<Long, Map<String, Object>> info, Long sid,
                                             String source) {
        Map<String, Object> s = info.get(sid);
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("studentId", sid);
        u.put("code", s == null ? null : s.get("code"));
        u.put("realName", s == null ? null : s.get("realName"));
        u.put("source", source);
        return u;
    }

    /**
     * 报名通道是否已经彻底关闭。
     * 只认最后一轮，且该轮补录窗口已经结束；否则管理员可能把还能报名的学生提前分配走。
     */
    private boolean registrationClosed(Integer round) {
        if (round < adminMapper.lastActiveRound()) return false;
        LocalDateTime refillEnd = stageService.getEndAt(round, STAGE_REFILL);
        return refillEnd == null || !refillEnd.isAfter(LocalDateTime.now());
    }

    /**
     * 收口安置：把本轮还没着落的学生一次性全部落到实训室。
     *
     * 顺序：
     *   1. 有待确认拟录取的 → 按顺位定第一个（其余作废）
     *   2. 没有任何拟录取的 → 优先同室空位，其次跨室（须服从跨室）
     *   3. includeRefusers=true 时，对不服从调剂的人也强制安置（即"约谈后统筹"）
     */
    @Transactional
    public Map<String, Object> finalizePlacement(Long adminId, Integer round, boolean includeRefusers) {
        requireAdmin(adminId);
        Publicity pub = adminMapper.latestPublicity(round);
        if (pub != null && ("PUBLISHED".equals(pub.status) || "ENDED".equals(pub.status))) {
            throw new BizException(2010, "最终名单已经公示，不能再改安置结果；如需调整请先作废公示");
        }

        LocalDateTime now = LocalDateTime.now();

        List<Map<String, Object>> caps = adminMapper.mentorCapacity(round);
        Map<Long, Integer> left = capacityLeft(round);
        Map<Long, Map<String, Object>> info = studentInfo();

        Map<Long, java.util.Set<Long>> labsOf = new LinkedHashMap<>();
        Map<Long, boolean[]> wishes = new LinkedHashMap<>();
        for (Map<String, Object> w : adminMapper.studentWishes(round)) {
            long sid = ((Number) w.get("studentId")).longValue();
            long labId = ((Number) w.get("labId")).longValue();
            labsOf.computeIfAbsent(sid, k -> new java.util.LinkedHashSet<>()).add(labId);
            boolean[] bw = wishes.computeIfAbsent(sid, k -> new boolean[]{false, false});
            if (asBool(w.get("adjustInLab"))) bw[0] = true;
            if (asBool(w.get("adjustCross"))) bw[1] = true;
        }

        List<Long> applied = adminMapper.appliedStudentIds(round);
        // 后面轮次已经录取的人不算本轮缺口，避免重复安置
        java.util.Set<Long> confirmed = new java.util.HashSet<>(adminMapper.confirmedFromRoundOn(round));
        // 最后一轮报名结束后，所有还没着落的学生都纳入（去重，避免和本轮已填报的人重复处理）
        java.util.Set<Long> toPlace = new java.util.LinkedHashSet<>(applied);
        if (registrationClosed(round)) {
            for (Map<String, Object> s : adminMapper.studentsWithoutPlacement()) {
                toPlace.add(((Number) s.get("studentId")).longValue());
            }
        }

        int settledSingle = 0, settledMulti = 0, inLab = 0, cross = 0, forced = 0;
        List<Long> stillUnplaced = new ArrayList<>();

        for (Long sid : toPlace) {
            if (confirmed.contains(sid)) continue;

            List<Offer> issued = offerMapper.findIssuedAdmitted(sid, round);
            if (!issued.isEmpty()) {
                Offer pick = null;
                for (Offer o : issued) {
                    if (left.getOrDefault(o.mentorId, 0) > 0) { pick = o; break; }
                }
                if (pick != null) {
                    offerMapper.updateStatus(pick.id, "CONFIRMED", now);
                    offerMapper.expireAllOther(sid, round, pick.id);
                    left.put(pick.mentorId, Math.max(0, left.getOrDefault(pick.mentorId, 0) - 1));
                    confirmed.add(sid);
                    if (issued.size() == 1) settledSingle++; else settledMulti++;
                    continue;
                }
                // 他所有意向的导师都满了：这些意向作废，下面按调剂意愿重新分配，
                // 绝对不能硬确认（否则就超录了）
                for (Offer o : issued) {
                    offerMapper.updateStatus(o.id, "EXPIRED", null);
                }
            }

            boolean[] w = wishes.getOrDefault(sid, new boolean[]{false, false});
            java.util.Set<Long> myLabs = labsOf.getOrDefault(sid, java.util.Set.of());

            // ① 同室调剂：必须是"服从实训室内部调剂"的人
            Long target = w[0] ? pickMentor(left, caps, myLabs, true) : null;
            String how = "同室调剂";
            if (target != null) {
                inLab++;
            } else if (w[1]) {
                // ② 跨室调剂：必须是"服从跨实训室调剂"的人
                target = pickMentor(left, caps, null, false);
                how = "跨室调剂";
                if (target != null) cross++;
            } else if (includeRefusers) {
                target = pickMentor(left, caps, null, false);
                how = "学院统筹（经约谈）";
                if (target != null) forced++;
            }

            if (target == null) {
                stillUnplaced.add(sid);
                continue;
            }

            long labId = 0;
            for (Map<String, Object> c : caps) {
                if (((Number) c.get("mentorId")).longValue() == target) {
                    labId = ((Number) c.get("labId")).longValue();
                    break;
                }
            }
            placeConfirmed(sid, target, labId, round, now, how);
            left.put(target, Math.max(0, left.getOrDefault(target, 0) - 1));
            confirmed.add(sid);
        }

        logMapper.insert(adminId, "ADMIN", "FINALIZE_PLACEMENT", "ROUND", round.longValue(),
                String.format("收口安置：单意向定案 %d，多意向定案 %d，同室 %d，跨室 %d，强制 %d，仍未安置 %d",
                        settledSingle, settledMulti, inLab, cross, forced, stillUnplaced.size()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("settledSingle", settledSingle);
        m.put("settledMulti", settledMulti);
        m.put("assignedInLab", inLab);
        m.put("assignedCross", cross);
        m.put("forced", forced);
        List<Map<String, Object>> leftUnplaced = new ArrayList<>();
        for (Long sid : stillUnplaced) {
            Map<String, Object> s = info.get(sid);
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("studentId", sid);
            u.put("code", s == null ? null : s.get("code"));
            u.put("realName", s == null ? null : s.get("realName"));
            leftUnplaced.add(u);
        }
        m.put("stillUnplaced", leftUnplaced);
        m.put("stillUnplacedCount", leftUnplaced.size());
        m.put("verify", placementVerify(round));
        return m;
    }

    /** 收口校验：每个已填报学生必须恰好有一个确认去向，且不能超过导师名额 */
    public Map<String, Object> placementVerify(Integer round) {
        List<Long> applied = adminMapper.appliedStudentIds(round);
        // 跨轮：只要在本轮或之后某一轮被录取，就算最终有着落
        java.util.Set<Long> confirmed = new java.util.HashSet<>(adminMapper.confirmedFromRoundOn(round));
        Map<Long, Map<String, Object>> info = studentInfo();

        List<Map<String, Object>> missing = new ArrayList<>();
        java.util.Set<Long> listed = new java.util.HashSet<>();
        for (Long sid : applied) {
            if (confirmed.contains(sid)) continue;
            Map<String, Object> s = info.get(sid);
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("studentId", sid);
            u.put("code", s == null ? null : s.get("code"));
            u.put("realName", s == null ? null : s.get("realName"));
            u.put("problem", "没有确认去向");
            missing.add(u);
            listed.add(sid);
        }

        // 报名通道关闭后，"从未报名"和"只在往轮报过名"的未安置学生也算缺口，
        // 否则会和「收口缺口」对不上，出现"还有 12 人没着落却能公示"的情况
        if (registrationClosed(round)) {
            for (Map<String, Object> s : adminMapper.studentsWithoutPlacement()) {
                long sid = ((Number) s.get("studentId")).longValue();
                if (listed.contains(sid)) continue;
                boolean never = ((Number) s.get("applicationCount")).intValue() == 0;
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("studentId", sid);
                u.put("code", s.get("code"));
                u.put("realName", s.get("realName"));
                u.put("problem", never ? "从未报名，尚未统筹" : "往轮未录取，尚未统筹");
                missing.add(u);
            }
        }

        List<Map<String, Object>> multi = new ArrayList<>();
        for (Map<String, Object> d : adminMapper.duplicateConfirmed(round)) {
            long sid = ((Number) d.get("studentId")).longValue();
            Map<String, Object> s = info.get(sid);
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("studentId", sid);
            u.put("code", s == null ? null : s.get("code"));
            u.put("realName", s == null ? null : s.get("realName"));
            u.put("problem", "有多条确认（" + d.get("cnt") + " 条）");
            multi.add(u);
        }

        List<Map<String, Object>> over = new ArrayList<>();
        for (Map<String, Object> c : adminMapper.mentorCapacity(round)) {
            int quota = ((Number) c.get("quota")).intValue();
            int cf = ((Number) c.get("confirmed")).intValue();
            if (cf > quota) {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("mentorName", c.get("mentorName"));
                u.put("labCode", c.get("labCode"));
                u.put("quota", quota);
                u.put("confirmed", cf);
                u.put("problem", "超录 " + (cf - quota) + " 人");
                over.add(u);
            }
        }

        int lastRound = adminMapper.lastActiveRound();
        boolean finalRound = round >= lastRound;
        // 非最终轮次：允许还有人在等后续补录，只要没有重复和超录就算通过
        boolean ok = multi.isEmpty() && over.isEmpty() && (finalRound ? missing.isEmpty() : true);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("finalRound", finalRound);
        m.put("lastRound", lastRound);
        m.put("appliedCount", applied.size());
        // 已确认 = 本轮及之后任意轮次有 CONFIRMED 的学生总数（含被统筹安置的非报名者）
        m.put("confirmedCount", adminMapper.confirmedFromRoundOn(round).size());
        m.put("missing", missing);
        m.put("duplicate", multi);
        m.put("overQuota", over);
        return m;
    }

    // ---------------- 操作日志 ----------------

    public Map<String, Object> logs(Integer page, Integer size, String action) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 200);
        List<com.college.selection.entity.OperationLog> list =
                logMapper.page(action, (p - 1) * s, s);
        int total = logMapper.count(action);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("list", list);
        m.put("total", total);
        m.put("page", p);
        m.put("size", s);
        m.put("actions", logMapper.distinctActions());
        return m;
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
