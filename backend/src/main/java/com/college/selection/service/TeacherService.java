package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.dto.ApplicantVO;
import com.college.selection.dto.ScoreReq;
import com.college.selection.dto.TeacherListsReq;
import com.college.selection.dto.TeacherListsVO;
import com.college.selection.entity.InterviewInvite;
import com.college.selection.entity.Mentor;
import com.college.selection.entity.Offer;
import com.college.selection.entity.SysUser;
import com.college.selection.mapper.ApplicationMapper;
import com.college.selection.mapper.InterviewInviteMapper;
import com.college.selection.mapper.MentorMapper;
import com.college.selection.mapper.OfferMapper;
import com.college.selection.mapper.OperationLogMapper;
import com.college.selection.mapper.SysUserMapper;
import com.college.selection.mapper.TeacherMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeacherService {

    /** 面试考核对应流程图第 4 个环节 */
    private static final int STAGE_INTERVIEW = 4;
    /** 拟录取确认对应第 5 个环节，offer 的确认截止时间取它的结束时间 */
    private static final int STAGE_CONFIRM = 5;
    /** 分轮补录：补录轮里导师同样要面试、发拟录取 */
    private static final int STAGE_REFILL = 6;

    private final TeacherMapper teacherMapper;
    private final OfferMapper offerMapper;
    private final ApplicationMapper applicationMapper;
    private final SysUserMapper userMapper;
    private final StageService stageService;
    private final OperationLogMapper logMapper;
    private final InterviewInviteMapper inviteMapper;
    private final MentorMapper mentorMapper;

    public TeacherService(TeacherMapper teacherMapper,
                          OfferMapper offerMapper,
                          ApplicationMapper applicationMapper,
                          SysUserMapper userMapper,
                          StageService stageService,
                          OperationLogMapper logMapper,
                          InterviewInviteMapper inviteMapper,
                          MentorMapper mentorMapper) {
        this.teacherMapper = teacherMapper;
        this.offerMapper = offerMapper;
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.stageService = stageService;
        this.logMapper = logMapper;
        this.inviteMapper = inviteMapper;
        this.mentorMapper = mentorMapper;
    }

    /** 取出当前登录的导师，校验角色和绑定关系 */
    private SysUser requireTeacher(Long teacherId) {
        SysUser u = userMapper.findById(teacherId);
        if (u == null || !"TEACHER".equals(u.role)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有导师账号可以执行该操作");
        }
        if (u.mentorId == null || u.labId == null) {
            throw new BizException(ErrorCode.NO_PERMISSION, "该导师账号还没有绑定实训室/导师档案");
        }
        return u;
    }

    public List<ApplicantVO> applicants(Long teacherId, Integer round) {
        SysUser t = requireTeacher(teacherId);
        List<ApplicantVO> list = teacherMapper.findApplicants(t.labId, round);

        // 打上"本室已入围""是我拉进来的"标记，前端据此显示面试范围
        List<Long> labInvited = inviteMapper.findInvitedStudentIds(t.labId, round);
        List<Long> mine = inviteMapper.findInvitedByMentor(t.mentorId, round);

        for (ApplicantVO a : list) {
            a.interviewed = a.scoreAttitude != null;
            a.invited = labInvited.contains(a.studentId);
            a.invitedByMe = mine.contains(a.studentId);
        }
        return list;
    }

    // ---------------- 面试入围 ----------------

    /**
     * 把学生拉进本室面试名单。
     * 实际流程是"导师先初选一部分人让他来面试"，不是所有人都面试，所以先入围再评分。
     */
    @Transactional
    public Map<String, Object> invite(Long teacherId, Integer round, List<Long> studentIds) {
        SysUser t = requireTeacher(teacherId);
        stageService.assertAnyOpen(round, STAGE_INTERVIEW, STAGE_REFILL);
        if (studentIds == null || studentIds.isEmpty()) {
            throw new BizException(2007, "请先选择要拉进面试名单的学生");
        }

        int added = 0;
        for (Long sid : studentIds) {
            if (teacherMapper.countAppliedToLab(sid, t.labId, round) == 0) {
                throw new BizException(2007, who(sid) + " 没有报名本实训室，不能拉进面试名单");
            }
            InterviewInvite iv = new InterviewInvite();
            iv.roundNo = round;
            iv.labId = t.labId;
            iv.mentorId = t.mentorId;
            iv.studentId = sid;
            added += inviteMapper.insert(iv);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("added", added);
        m.put("total", inviteMapper.findInvitedStudentIds(t.labId, round).size());
        return m;
    }

    @Transactional
    public Map<String, Object> uninvite(Long teacherId, Integer round, Long studentId) {
        SysUser t = requireTeacher(teacherId);
        stageService.assertAnyOpen(round, STAGE_INTERVIEW, STAGE_REFILL);

        // 已经进了我的名单就不能直接取消入围，否则名单和面试范围会对不上
        for (Offer o : offerMapper.findByMentor(t.mentorId, round)) {
            if (o.studentId.equals(studentId) && !"VOID".equals(o.status) && !"DECLINED".equals(o.status)) {
                throw new BizException(2007,
                        who(studentId) + " 已经在你的名单里（" + o.type + "），请先从名单中移除再取消入围");
            }
        }

        inviteMapper.delete(round, t.mentorId, studentId);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", inviteMapper.findInvitedStudentIds(t.labId, round).size());
        return m;
    }

    /** 本室面试名单（入围者的并集），只有这些人能评分、能进三份名单 */
    public List<ApplicantVO> interviewList(Long teacherId, Integer round) {
        SysUser t = requireTeacher(teacherId);
        List<ApplicantVO> list = inviteMapper.findInterviewList(t.labId, round);
        for (ApplicantVO a : list) {
            a.interviewed = a.scoreAttitude != null;
            a.invited = true;
        }
        return list;
    }

    @Transactional
    public void saveScore(Long teacherId, Long studentId, Integer round, ScoreReq req) {
        SysUser t = requireTeacher(teacherId);

        if (teacherMapper.countAppliedToLab(studentId, t.labId, round) == 0) {
            throw new BizException(ErrorCode.NO_PERMISSION, "该学生没有报名本实训室，不能评分");
        }
        teacherMapper.upsertScore(studentId, t.labId, round, t.mentorId, req, teacherId);
    }

    public TeacherListsVO lists(Long teacherId, Integer round) {
        SysUser t = requireTeacher(teacherId);
        TeacherListsVO vo = new TeacherListsVO();
        vo.round = round;
        vo.mentorId = t.mentorId;
        vo.mentorName = t.realName;
        vo.quota = offerMapper.lockMentorQuota(t.mentorId);

        List<Offer> all = offerMapper.findByMentor(t.mentorId, round);
        vo.admitted = filter(all, "ADMITTED");
        vo.waitlist = filter(all, "WAITLIST");
        vo.adjustCandidates = filter(all, "ADJUST_CANDIDATE");
        return vo;
    }

    private List<Offer> filter(List<Offer> all, String type) {
        List<Offer> out = new ArrayList<>();
        for (Offer o : all) {
            if (type.equals(o.type)) {
                out.add(o);
            }
        }
        return out;
    }

    /**
     * 提交三份名单。
     *
     * 规则：
     *   1. 只能在"面试考核"或"分轮补录"阶段提交
     *   2. 拟录取人数 ≤ 该导师名额
     *   3. 每个学生都必须报过本室
     *   4. 已经确认了其他导师的学生不能再发拟录取
     *   5. 重复提交 = 覆盖本导师本轮名单
     *   6. 首轮面试阶段不自动确认（等进入确认阶段统一结算）；
     *      分轮补录阶段没有再走"拟录取确认"环节，所以提交时就地确认只有 1 个意向的学生
     *   7. 允许在「拟录取与学生确认」阶段补交/修正名单：
     *      管理员切到阶段 5 之后，导师常常还要补录一两个人或改名单，
     *      如果只允许阶段 4 提交，切到阶段 5 就会卡死（提示不在面试窗口内）。
     *      此时提交按"已进入结算阶段"处理，只有 1 个意向的学生当场确认。
     */
    @Transactional
    public void saveLists(Long teacherId, Integer round, TeacherListsReq req) {
        SysUser t = requireTeacher(teacherId);
        stageService.assertAnyOpen(round, STAGE_INTERVIEW, STAGE_CONFIRM, STAGE_REFILL);
        boolean refill = stageService.isOpen(round, STAGE_REFILL);
        boolean confirmStage = stageService.isOpen(round, STAGE_CONFIRM);

        List<Long> admitted = req.admitted == null ? Collections.emptyList() : req.admitted;
        List<Long> waitlist = req.waitlist == null ? Collections.emptyList() : req.waitlist;

        // 内部调剂：优先用 adjustList（带目标导师），否则退回旧字段（不带目标）
        List<Long> adjust = new ArrayList<>();
        List<Long> adjustTarget = new ArrayList<>();
        if (req.adjustList != null && !req.adjustList.isEmpty()) {
            for (TeacherListsReq.AdjustItem it : req.adjustList) {
                if (it == null || it.studentId == null) continue;
                adjust.add(it.studentId);
                adjustTarget.add(it.targetMentorId);
            }
        } else if (req.adjustCandidates != null) {
            for (Long sid : req.adjustCandidates) {
                adjust.add(sid);
                adjustTarget.add(null);
            }
        }

        Integer quota = offerMapper.lockMentorQuota(t.mentorId);
        if (quota == null) {
            throw new BizException(ErrorCode.NO_PERMISSION, "导师档案不存在");
        }
        if (admitted.size() > quota) {
            throw new BizException(ErrorCode.QUOTA_FULL,
                    String.format("拟录取 %d 人超过名额 %d 人", admitted.size(), quota));
        }

        // 首轮取阶段 5 的结束时间；补录轮走的是阶段 6，截止就取补录窗口的结束时间
        LocalDateTime deadline = refill
                ? stageService.getEndAt(round, STAGE_REFILL)
                : stageService.getEndAt(round, STAGE_CONFIRM);
        LocalDateTime now = LocalDateTime.now();

        // 已经确认了本导师的学生，不能被悄悄从名单里移除（否则学生状态会莫名回退）
        for (Long sid : offerMapper.findConfirmedStudentIds(t.mentorId, round)) {
            if (!admitted.contains(sid)) {
                throw new BizException(2007,
                        who(sid) + " 已经确认了你的录取意向，不能直接移出名单；如需撤销请联系学院管理员");
            }
        }

        offerMapper.deleteByMentor(t.mentorId, round);

        List<Long> allStudents = new ArrayList<>();
        allStudents.addAll(admitted);
        allStudents.addAll(waitlist);
        allStudents.addAll(adjust);
        for (Long studentId : allStudents) {
            if (teacherMapper.countAppliedToLab(studentId, t.labId, round) == 0) {
                throw new BizException(2007, who(studentId) + " 没有报名本实训室");
            }
        }

        // 面试阶段：只有入围的人才能进三份名单（实际流程是导师先初选一部分人来面试）
        if (stageService.isOpen(round, STAGE_INTERVIEW)) {
            List<Long> invited = inviteMapper.findInvitedStudentIds(t.labId, round);
            for (Long sid : allStudents) {
                if (!invited.contains(sid)) {
                    throw new BizException(2007,
                            who(sid) + " 还没有进入本室面试名单，不能写入名单（请先在「报名学生」里拉进面试）");
                }
            }
            // 名单是"面试之后"才出的，没打分的人不能被写进任何一份名单
            Map<Long, Double> scored = new LinkedHashMap<>();
            for (ApplicantVO a : inviteMapper.findInterviewList(t.labId, round)) {
                if (a.scoreTotal != null) {
                    scored.put(a.studentId, a.scoreTotal);
                }
            }
            for (Long sid : allStudents) {
                if (!scored.containsKey(sid)) {
                    throw new BizException(2007,
                            who(sid) + " 还没有面试评分，请先在「面试评价」里打分，再写入名单");
                }
            }
        }

        for (Long studentId : admitted) {
            if (offerMapper.countAnyConfirmed(studentId, round) > 0) {
                throw new BizException(2007, who(studentId) + " 已确认了其他导师，不能再发拟录取");
            }
        }

        List<Offer> inserted = new ArrayList<>();
        for (int i = 0; i < admitted.size(); i++) {
            // 拟录取也带顺位（1 为第一顺位），自动录取和递补都按这个顺序
            inserted.add(insert(t, round, admitted.get(i), "ADMITTED", i + 1, now, deadline));
        }
        for (int i = 0; i < waitlist.size(); i++) {
            inserted.add(insert(t, round, waitlist.get(i), "WAITLIST", i + 1, now, deadline));
        }
        // 内部调剂必须"调给同室的另一位导师"：校验目标存在、同室、不是自己
        for (int i = 0; i < adjust.size(); i++) {
            Long target = adjustTarget.get(i);
            if (target == null) continue;
            if (target.equals(t.mentorId)) {
                throw new BizException(2007, "内部调剂不能调给自己，请直接放进拟录取");
            }
            Mentor tm = mentorMapper.findById(target);
            if (tm == null) {
                throw new BizException(2007, "调剂目标导师不存在：" + target);
            }
            if (!tm.labId.equals(t.labId)) {
                throw new BizException(2007, "内部调剂只能调给本实训室的导师，"
                        + tm.name + " 属于其他实训室");
            }
        }

        for (int i = 0; i < adjust.size(); i++) {
            inserted.add(insert(t, round, adjust.get(i), "ADJUST_CANDIDATE", i + 1, now, deadline,
                    adjustTarget.get(i)));
        }

        // 注意：首轮这里【不做】自动确认。
        // 面试阶段导师是并行提交名单的，如果一发就确认，第一个发名单的导师会把学生锁死，
        // "收到 2 个以上意向由学生自主选择"就永远不会发生。
        // 统一确认放到进入「拟录取与学生确认」阶段时由 AdminService.settle 批量结算。
        //
        // 分轮补录阶段没有再走"拟录取确认"环节，所以就地结算：
        // 只填了 1 个意向（不可能再收到第二份拟录取）并且已发 1 份的学生直接确认；
        // 填了多个意向的学生等所有导师提交完，在本阶段自己选。
        //
        // 同理，如果当前已经处在「拟录取与学生确认」阶段（管理员切过来了），
        // 说明结算时机已到，导师补交的名单也按同样规则当场确认。
        if (refill || confirmStage) {
            for (Long sid : admitted) {
                if (applicationMapper.countItems(sid, round) == 1
                        && offerMapper.countAdmittedIssued(sid, round) == 1) {
                    offerMapper.autoConfirmByStudent(sid, round);
                }
            }
        }
    }

    /**
     * 自动录取。
     *
     * 默认是导师人工挑选；这个接口给"省时省心"的场景用：
     *   sortBy = score  → 按面试总分从高到低
     *   sortBy = manual → 按导师已有的排序（rank_no），没排过的按分数排在后面
     * 录满名额为止，剩下的人自动进递补。
     */
    @Transactional
    public Map<String, Object> autoAdmit(Long teacherId, Integer round, String sortBy) {
        SysUser t = requireTeacher(teacherId);
        stageService.assertAnyOpen(round, STAGE_INTERVIEW, STAGE_CONFIRM, STAGE_REFILL);

        Integer quota = offerMapper.lockMentorQuota(t.mentorId);
        if (quota == null) {
            throw new BizException(ErrorCode.NO_PERMISSION, "导师档案不存在");
        }

        // 候选池 = 本室面试名单；findInterviewList 已按分数从高到低排（未评分在最后）
        List<ApplicantVO> pool = inviteMapper.findInterviewList(t.labId, round);
        if (pool.isEmpty()) {
            throw new BizException(2007, "本室还没有面试名单，请先在「报名学生」里把学生拉进面试");
        }

        List<Long> ordered = new ArrayList<>();
        for (ApplicantVO a : pool) {
            // 只有评过分的人参与自动录取，否则会被"未评分不能进名单"的校验挡住
            if (a.scoreTotal != null) {
                ordered.add(a.studentId);
            }
        }
        if (ordered.isEmpty()) {
            throw new BizException(2007, "面试名单里还没有已评分的学生，无法自动录取");
        }

        if ("manual".equalsIgnoreCase(sortBy)) {
            Map<Long, Integer> rank = new LinkedHashMap<>();
            for (Offer o : offerMapper.findByMentor(t.mentorId, round)) {
                if (o.rankNo != null) {
                    rank.put(o.studentId, o.rankNo);
                }
            }
            ordered.sort(Comparator.comparingInt(sid -> rank.getOrDefault(sid, Integer.MAX_VALUE)));
        }

        List<Long> admitted = new ArrayList<>();
        List<Long> waitlist = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            if (i < quota) {
                admitted.add(ordered.get(i));
            } else {
                waitlist.add(ordered.get(i));
            }
        }

        TeacherListsReq req = new TeacherListsReq();
        req.round = round;
        req.admitted = admitted;
        req.waitlist = waitlist;
        req.adjustCandidates = new ArrayList<>();
        saveLists(teacherId, round, req);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sortBy", sortBy == null || sortBy.isBlank() ? "score" : sortBy);
        m.put("quota", quota);
        m.put("admitted", admitted.size());
        m.put("waitlist", waitlist.size());
        return m;
    }

    // ---------------- 内部调剂（收件方视角） ----------------

    /** 别人指定调剂给我的学生 */
    public List<Offer> adjustInbox(Long teacherId, Integer round) {
        SysUser t = requireTeacher(teacherId);
        return offerMapper.findAdjustInbox(t.mentorId, round);
    }

    /**
     * 接收调剂：学生直接变成我的拟录取（占我的名额，不再二次面试）。
     * 原记录置为 VOID 留痕。
     */
    @Transactional
    public Map<String, Object> acceptAdjust(Long teacherId, Integer round, Long offerId) {
        SysUser t = requireTeacher(teacherId);
        stageService.assertAnyOpen(round, STAGE_INTERVIEW, STAGE_CONFIRM, STAGE_REFILL);

        Offer o = offerMapper.lockById(offerId);
        if (o == null) {
            throw new BizException(4041, "调剂记录不存在");
        }
        if (!"ADJUST_CANDIDATE".equals(o.type) || !"ISSUED".equals(o.status)) {
            throw new BizException(2006, "该调剂记录已处理，不能重复接收");
        }
        if (o.targetMentorId == null || !o.targetMentorId.equals(t.mentorId)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "这条调剂不是指定给你的");
        }

        Integer quota = offerMapper.lockMentorQuota(t.mentorId);
        int used = offerMapper.countAdmittedByMentor(t.mentorId, round);
        if (quota == null || used >= quota) {
            throw new BizException(ErrorCode.QUOTA_FULL,
                    String.format("你的名额已满（%d/%d），不能再接收调剂学生", used, quota));
        }

        // 两种情况会导致唯一键冲突，必须提前拦住（否则数据库直接抛 500）
        if (offerMapper.countActiveOffer(o.studentId, t.mentorId, round) > 0) {
            throw new BizException(2007,
                    who(o.studentId) + " 已经是你的拟录取/递补，不能重复接收这条调剂");
        }
        if (offerMapper.countAnyConfirmed(o.studentId, round) > 0) {
            throw new BizException(2007,
                    who(o.studentId) + " 已经确认了其他导师的去向，不能再接收");
        }

        o.status = "VOID";
        o.remark = "已由 " + t.realName + " 接收为拟录取";
        offerMapper.updateStatus(o.id, "VOID", null);

        Offer created = insert(t, round, o.studentId, "ADMITTED",
                offerMapper.maxAdmittedRank(t.mentorId, round) + 1,
                LocalDateTime.now(), stageService.getEndAt(round, STAGE_CONFIRM), null);

        logMapper.insertWithState(teacherId, "TEACHER", "ADJUST_ACCEPT", "OFFER", o.id,
                "ADJUST_CANDIDATE", "ADMITTED", "接收调剂学生 " + who(o.studentId) + " 为拟录取");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", o.studentId);
        m.put("newOfferId", created.id);
        return m;
    }

    /** 拒绝调剂：退回发起人（学生回到调剂池） */
    @Transactional
    public Map<String, Object> rejectAdjust(Long teacherId, Integer round, Long offerId, String reason) {
        SysUser t = requireTeacher(teacherId);

        Offer o = offerMapper.lockById(offerId);
        if (o == null) {
            throw new BizException(4041, "调剂记录不存在");
        }
        if (o.targetMentorId == null || !o.targetMentorId.equals(t.mentorId)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "这条调剂不是指定给你的");
        }
        if (!"ISSUED".equals(o.status)) {
            throw new BizException(2006, "该调剂记录已处理");
        }

        offerMapper.updateStatus(o.id, "DECLINED", null);
        logMapper.insertWithState(teacherId, "TEACHER", "ADJUST_REJECT", "OFFER", o.id,
                "ADJUST_CANDIDATE", "DECLINED",
                "拒绝调剂学生 " + who(o.studentId) + (reason == null || reason.isBlank() ? "" : "，原因：" + reason));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", o.studentId);
        return m;
    }

    /** 把数字 id 换成人看得懂的名字，别在报错里甩 ID */
    private String who(Long studentId) {
        SysUser u = userMapper.findById(studentId);
        return u == null ? ("学生 #" + studentId) : (u.realName + "（" + u.code + "）");
    }

    /**
     * 撤销已确认的录取。
     *
     * 做三件事：
     *   1. 把那条 offer 置为 VOID（不删除，留痕）
     *   2. 按递补顺位把下一名提升为拟录取
     *   3. 写操作日志（含前后状态）
     */
    @Transactional
    public java.util.Map<String, Object> revoke(Long teacherId, Integer round, Long studentId, String reason) {
        SysUser t = requireTeacher(teacherId);
        if (reason == null || reason.isBlank()) {
            throw new BizException(2007, "撤销录取必须填写原因");
        }

        Offer target = null;
        java.util.List<Offer> all = offerMapper.findByMentor(t.mentorId, round);
        for (Offer o : all) {
            if (o.studentId.equals(studentId) && "CONFIRMED".equals(o.status)) {
                target = o;
                break;
            }
        }
        if (target == null) {
            throw new BizException(2006, "该学生没有确认你的录取意向，无需撤销");
        }

        offerMapper.updateStatus(target.id, "VOID", null);
        logMapper.insertWithState(teacherId, "TEACHER", "REVOKE_ADMIT", "OFFER", target.id,
                "CONFIRMED", "VOID", "撤销 " + who(studentId) + " 的录取，原因：" + reason);

        // 顺位递补：把本导师递补里 rank 最小的那个提为拟录取
        Offer next = null;
        for (Offer o : all) {
            if ("WAITLIST".equals(o.type) && "ISSUED".equals(o.status)) {
                if (next == null || (o.rankNo != null && (next.rankNo == null || o.rankNo < next.rankNo))) {
                    next = o;
                }
            }
        }
        Long promotedStudentId = null;
        if (next != null) {
            offerMapper.promoteToAdmitted(next.id,
                    offerMapper.maxAdmittedRank(t.mentorId, round) + 1,
                    stageService.getEndAt(round, STAGE_CONFIRM));
            promotedStudentId = next.studentId;
        }

        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("revokedStudentId", studentId);
        m.put("promotedStudentId", promotedStudentId);
        m.put("promotedName", promotedStudentId == null ? null : who(promotedStudentId));
        return m;
    }

    private Offer insert(SysUser t, Integer round, Long studentId, String type, Integer rank,
                         LocalDateTime now, LocalDateTime deadline) {
        return insert(t, round, studentId, type, rank, now, deadline, null);
    }

    private Offer insert(SysUser t, Integer round, Long studentId, String type, Integer rank,
                         LocalDateTime now, LocalDateTime deadline, Long targetMentorId) {
        Offer o = new Offer();
        o.studentId = studentId;
        o.labId = t.labId;
        o.mentorId = t.mentorId;
        o.targetMentorId = targetMentorId;
        o.roundNo = round;
        o.type = type;
        o.rankNo = rank;
        o.status = "ISSUED";
        o.issuedAt = now;
        o.confirmDeadline = deadline;
        offerMapper.insert(o);
        return o;
    }
}
