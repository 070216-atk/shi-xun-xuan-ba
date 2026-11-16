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
import com.college.selection.entity.TransferRequest;
import com.college.selection.mapper.AdminMapper;
import com.college.selection.mapper.MentorMapper;
import com.college.selection.mapper.NotificationMapper;
import com.college.selection.mapper.OfferMapper;
import com.college.selection.mapper.OperationLogMapper;
import com.college.selection.mapper.SysUserMapper;
import com.college.selection.mapper.TransferRequestMapper;
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
    /** 意向填报对应第 3 个环节（报名通道的第一段） */
    private static final int STAGE_APPLY = 3;
    /** 分轮补录对应第 6 个环节（补录轮开启后直接进入报名） */
    private static final int STAGE_REFILL = 6;
    /** 拟录取与学生确认对应第 5 个环节 */
    private static final int STAGE_CONFIRM = 5;
    /** 实训室开放日：名额公布在这里才有意义 */
    private static final int STAGE_OPEN_DAY = 2;
    /** 结果公示与正式入组 */
    private static final int STAGE_PUBLICITY = 8;

    private final AdminMapper adminMapper;
    private final OfferMapper offerMapper;
    private final MentorMapper mentorMapper;
    private final SysUserMapper userMapper;
    private final StageService stageService;
    private final OperationLogMapper logMapper;
    private final NotificationMapper notificationMapper;
    private final TransferRequestMapper transferMapper;

    public AdminService(AdminMapper adminMapper,
                        OfferMapper offerMapper,
                        MentorMapper mentorMapper,
                        SysUserMapper userMapper,
                        StageService stageService,
                        OperationLogMapper logMapper,
                        NotificationMapper notificationMapper,
                        TransferRequestMapper transferMapper) {
        this.adminMapper = adminMapper;
        this.offerMapper = offerMapper;
        this.mentorMapper = mentorMapper;
        this.userMapper = userMapper;
        this.stageService = stageService;
        this.logMapper = logMapper;
        this.notificationMapper = notificationMapper;
        this.transferMapper = transferMapper;
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
        // 名额公布只在「开放日 / 意向填报 / 分轮补录」有意义：
        // 结果都公示完了还广播"还有 N 个空位"会误导学生
        stageService.assertAnyOpen(round, STAGE_OPEN_DAY, STAGE_APPLY, STAGE_REFILL);
        List<QuotaVO> qs = quotas(round);
        int total = 0;
        for (QuotaVO q : qs) {
            total += (q.remaining == null ? 0 : q.remaining);
        }
        String remark = String.format("公布第 %d 轮剩余名额，共 %d 个", round, total);
        logMapper.insert(adminId, "ADMIN", "PUBLISH_QUOTA", "ROUND", round.longValue(), remark);
        if (total > 0) {
            notificationMapper.insertForAllStudents("QUOTA", "剩余名额公布",
                    String.format("第 %d 轮目前还有 %d 个名额，请关注补录安排", round, total));
        }

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
     * 本轮学生自己发起的跨室转组申请（已录取但去向不满意）。
     * 管理端只在 ② 跨室调剂的下面展示，接收/拒绝由目标导师处理。
     */
    public List<TransferRequest> transfers(Long adminId, Integer round) {
        requireAdmin(adminId);
        return transferMapper.findByRound(round);
    }

    /**
     * 第一优先级：实训室内部调剂。
     * 把学生安排到本室的某个导师，直接置为已确认（统筹调剂不需要学生再确认）。
     */
    @Transactional
    public void adjustInternal(Long adminId, Integer round, Long mentorId, List<Long> studentIds) {
        requireAdmin(adminId);
        stageService.assertOpen(round, STAGE_ADJUST);
        // 室内调剂的第一优先级含义是"在这个学生报过的实训室里换导师"，
        // 所以本轮报过名的学生必须报过目标导师所在的实训室，否则属于跨室调剂
        if (mentorId == null || studentIds == null || studentIds.isEmpty()) {
            throw new BizException(2008, "必须指定导师和学生");
        }
        Mentor m = mentorMapper.findById(mentorId);
        if (m == null) {
            throw new BizException(2008, "导师不存在：" + mentorId);
        }
        for (Long sid : studentIds) {
            // 室内调剂的含义是"在他本轮报过的实训室里换导师"，
            // 没报过这个室（包括本轮根本没报名的）都不能走室内
            if (adminMapper.countStudentAppliedLab(sid, m.labId, round) == 0) {
                throw new BizException(2008,
                        who(sid) + " 本轮没有报过 " + m.name + " 所在的实训室，不属于室内调剂，请走跨室调剂或指定安置");
            }
        }
        place(adminId, round, mentorId, studentIds, "统筹调剂-室内调剂", "ADJUST_INTERNAL");
    }

    /**
     * 第二优先级：全院跨室调剂。
     * 必须先完成第一优先级（还有答应室内调剂的学生没着落时不允许进这一级）。
     */
    @Transactional
    public void adjustCross(Long adminId, Integer round, Long mentorId, List<Long> studentIds) {
        requireAdmin(adminId);
        stageService.assertOpen(round, STAGE_ADJUST);
        if (mentorId == null || studentIds == null || studentIds.isEmpty()) {
            throw new BizException(2008, "必须指定导师和学生");
        }
        // 只拦"室内还放得下"的人：报过的实训室全满的学生再卡着不走，跨室就永远做不了
        int pending = adminMapper.countPlaceableInternal(round);
        if (pending > 0) {
            throw new BizException(ErrorCode.ADJUST_ORDER_WRONG,
                    "还有 " + pending + " 名服从室内调剂的学生在本室还有空位，不能跳过第一优先级直接做跨室调剂");
        }
        // 跨室调剂只对勾选了"服从跨室调剂"的学生有效，不能把人硬塞去没报过、也没同意的实训室；
        // 另外"跨室"指的是换到别的实训室，学生自己报过的室属于①，不能从这里安排
        Mentor target = mentorMapper.findById(mentorId);
        if (target == null) {
            throw new BizException(2008, "导师不存在：" + mentorId);
        }
        for (Long sid : studentIds) {
            if (adminMapper.countStudentObeyCross(sid, round) == 0) {
                throw new BizException(2008,
                        who(sid) + " 本轮没有勾选「服从跨室调剂」，不能跨室安排，请改用「指定安置」并写明原因");
            }
            if (adminMapper.countStudentAppliedLab(sid, target.labId, round) > 0) {
                throw new BizException(2008,
                        who(sid) + " 本轮报过 " + target.name + " 所在的实训室，这属于①室内调剂，不能走②跨室");
            }
        }
        place(adminId, round, mentorId, studentIds, "统筹调剂-跨室调剂", "ADJUST_CROSS");
    }

    /** 把一个或多个学生直接安排给某个导师 */
    private void place(Long adminId, Integer round, Long mentorId, List<Long> studentIds,
                       String remark, String action) {
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
            // 留痕：管理端「统筹调剂」页的 ①② 记录就从这里读
            logMapper.insertWithState(adminId, "ADMIN", action, "OFFER", o.id, null, "CONFIRMED",
                    remark + " " + who(studentId) + " → " + mentor.name);
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
    public synchronized Map<String, Object> switchStage(Long adminId, Integer round, Integer stageNo, Integer hours) {
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
        // 结算只能发生在「拟录取与学生确认」或补录轮。
        // 否则在填报/面试阶段点一下，就会把还没发完名单的意向直接定案。
        stageService.assertAnyOpen(round, STAGE_CONFIRM, STAGE_REFILL);
        return settleOffers(round);
    }

    private Map<String, Object> settleOffers(Integer round) {
        // 先按阶段 5 的真实窗口刷新确认截止时间（发名单时阶段 5 可能还没配置）
        LocalDateTime confirmEnd = stageService.getEndAt(round, STAGE_CONFIRM);
        if (confirmEnd != null) {
            offerMapper.refreshIssuedDeadline(round, confirmEnd);
        }

        List<Long> sids = adminMapper.studentsWithIssuedAdmitted(round);
        int auto = 0, multi = 0, skipped = 0, already = 0;
        for (Long sid : sids) {
            // 已经确认过去向的学生不再自动确认：否则后面再来一个意向就会变成两条确认
            if (offerMapper.findConfirmed(sid, round) != null) {
                already++;
                continue;
            }
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
        String remark = String.format("结算第 %d 轮：自动确认 %d 人，多意向待学生选择 %d 人，因名额已满暂缓 %d 人，已有确认跳过 %d 人",
                round, auto, multi, skipped, already);
        logMapper.insert(null, "SYSTEM", "SETTLE_OFFERS", "ROUND", round.longValue(), remark);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("autoConfirmed", auto);
        m.put("multiChoice", multi);
        m.put("skippedQuotaFull", skipped);
        m.put("alreadyConfirmed", already);
        return m;
    }

    // ---------------- 公示 ----------------

    public Publicity latestPublicity(Integer round) {
        return adminMapper.latestPublicity(round);
    }

    /** 发布公示。加锁 + 事务：防止连点两次生成两条进行中的公示 */
    @Transactional
    public synchronized Publicity publish(Long adminId, Integer round, Integer days) {
        requireAdmin(adminId);
        // 公示只能是第 8 环节；在别的阶段点发布等于跳过调剂/补录
        stageService.assertOpen(round, STAGE_PUBLICITY);
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
                    String.format("还不能公示：还有 %d 人没有实训室、%d 人有重复去向、%d 位导师超录。请先在「安置检查」里处理后重试",
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

        logMapper.insert(adminId, "ADMIN", "PUBLISH", "PUBLICITY", p.id,
                String.format("发布第 %d 轮公示，历时 %d 天", round, d));
        notificationMapper.insertForAllStudents("PUBLICITY", "最终名单已公示",
                String.format("第 %d 轮最终名单已公示，可在「录取结果」查看；如有异议请联系学院", round));
        return p;
    }

    public void endPublicity(Long adminId, Integer round) {
        requireAdmin(adminId);
        stageService.assertOpen(round, STAGE_PUBLICITY);
        Publicity last = adminMapper.latestPublicity(round);
        if (last == null || !"PUBLISHED".equals(last.status)) {
            throw new BizException(2010, "当前没有进行中的公示");
        }
        // 以日志里的未处理异议为准（objection_count 只是冗余计数）
        int unresolved = logMapper.unresolvedObjections(last.id).size();
        if (unresolved > 0) {
            throw new BizException(ErrorCode.PENDING_OBJECTION,
                    "还有 " + unresolved + " 条异议未处理，不能结束公示");
        }
        adminMapper.updatePublicity(last.id, "ENDED", LocalDateTime.now());
        logMapper.insert(adminId, "ADMIN", "PUBLICITY_END", "PUBLICITY", last.id,
                String.format("第 %d 轮公示结束", round));
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
        if ("OPEN".equals(adminMapper.roundStatus(round))) {
            throw new BizException(2011, "第 " + round + " 轮已经在进行中，不用重复开启");
        }
        // 开补录轮 = 关掉上一轮。上一轮还停在报名/面试/确认阶段时不允许开，
        // 否则会出现"两轮同时在报名"，学生的意向到底算哪一轮会乱。
        Stage prevStage = stageService.current(round - 1);
        if (prevStage != null && prevStage.stageNo < STAGE_ADJUST) {
            throw new BizException(2011,
                    "第 " + (round - 1) + " 轮还停在「" + prevStage.name
                            + "」，要等它走到「统筹调剂录取」或「结果公示」后才能开补录轮");
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

    /**
     * 登记一条公示异议。
     * 不新建表：写在 operation_log（action=OBJECTION，remark="学号|原因"），
     * 这样刷新页面、换台电脑都能看到，处理记录也天然留痕。
     */
    @Transactional
    public void addObjection(Long adminId, Integer round, String studentCode, String reason) {
        requireAdmin(adminId);
        stageService.assertOpen(round, STAGE_PUBLICITY);
        Publicity p = adminMapper.latestPublicity(round);
        if (p == null || !"PUBLISHED".equals(p.status)) {
            throw new BizException(2010, "当前没有进行中的公示");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(2012, "异议内容不能为空");
        }
        String r = reason.trim();
        if (r.length() > 500) {
            throw new BizException(2012, "异议内容最多 500 字");
        }
        String code = studentCode == null ? "" : studentCode.trim();
        logMapper.insert(adminId, "ADMIN", "OBJECTION", "PUBLICITY", p.id, code + "|" + r);
        adminMapper.addObjection(p.id);
    }

    /** 当前公示里还没处理的异议 */
    public List<Map<String, Object>> objections(Integer round) {
        Publicity p = adminMapper.latestPublicity(round);
        List<Map<String, Object>> out = new ArrayList<>();
        if (p == null) return out;
        for (Map<String, Object> row : logMapper.unresolvedObjections(p.id)) {
            String remark = String.valueOf(row.get("remark"));
            int idx = remark.indexOf('|');
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.get("id"));
            m.put("createdAt", row.get("createdAt"));
            m.put("operatorName", row.get("operatorName"));
            m.put("studentCode", idx < 0 ? "" : remark.substring(0, idx));
            m.put("reason", idx < 0 ? remark : remark.substring(idx + 1));
            out.add(m);
        }
        return out;
    }

    /** 处理完异议后清零（写一条 OBJECTION_RESOLVE，旧异议不再计入），之后才允许结束公示 */
    @Transactional
    public void resolveObjections(Long adminId, Integer round) {
        requireAdmin(adminId);
        stageService.assertOpen(round, STAGE_PUBLICITY);
        Publicity p = adminMapper.latestPublicity(round);
        if (p == null) {
            throw new BizException(2010, "还没有公示记录");
        }
        int pending = logMapper.unresolvedObjections(p.id).size();
        if (pending == 0) {
            return;
        }
        logMapper.insert(adminId, "ADMIN", "OBJECTION_RESOLVE", "PUBLICITY", p.id,
                "异议处理完毕，本次清除 " + pending + " 条");
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

    // ---------------- 安置检查（确保每个人都有实训室） ----------------

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

    /**
     * 管理员指定安置（约谈后的"同意的去处"）。
     *
     * 这里是管理员明确指定去哪个导师，并且必须写原因，
     * 结果会直接变成"已确认"，备注写明"学院统筹安置"，学生在自己页面能看到这条说明。
     */
    @Transactional
    public Map<String, Object> assignPlacement(Long adminId, Integer round, Long studentId,
                                               Long mentorId, String reason) {
        requireAdmin(adminId);
        // 指定安置属于统筹调剂的一部分：还没走到这一步（初选/拟录取/补录都没做完）就不能先把人定下来
        stageService.assertOpen(round, STAGE_ADJUST);
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
        if (reason.length() > 200) {
            throw new BizException(2007, "安置原因最多 200 字");
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
        // 腾出名额 ≠ 还有空位：管理员可能刚把名额调小过
        Integer quota = offerMapper.lockMentorQuota(mentorId);
        int used = offerMapper.countAdmittedByMentor(mentorId, round);
        if (quota == null || used >= quota) return;
        Offer next = wl.get(0);
        offerMapper.promoteToAdmitted(next.id,
                offerMapper.maxAdmittedRank(mentorId, round) + 1,
                stageService.getEndAt(round, STAGE_CONFIRM));
        notificationMapper.insert(next.studentId, "OFFER", "递补成功",
                "你已从递补名单提升为拟录取，请尽快确认");
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
     *
     * 报名通道 = 「意向填报」+「分轮补录」两段窗口，只要其中任何一段还没结束，
     * 学生就还能新报名，管理员不能把没报名的学生提前安排掉。
     * （早期实现只看补录窗口，第 1 轮补录窗口在时间线上早于填报窗口时就误判为已关闭。）
     */
    private boolean registrationClosed(Integer round) {
        if (round < adminMapper.lastActiveRound()) return false;
        LocalDateTime now = LocalDateTime.now();
        for (int stageNo : new int[]{STAGE_APPLY, STAGE_REFILL}) {
            LocalDateTime end = stageService.getEndAt(round, stageNo);
            if (end != null && end.isAfter(now)) return false;
        }
        return true;
    }

    /** 安置校验：每个已填报学生必须恰好有一个确认去向，且不能超过导师名额 */
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
        // action 支持逗号分隔的多选，如 "ADJUST_INTERNAL,ADJUST_CROSS"
        List<String> actions = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            for (String a : action.split(",")) {
                if (!a.isBlank()) actions.add(a.trim());
            }
        }
        List<com.college.selection.entity.OperationLog> list =
                logMapper.page(actions, (p - 1) * s, s);
        int total = logMapper.count(actions);

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
