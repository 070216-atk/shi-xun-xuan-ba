package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.dto.TransferReq;
import com.college.selection.entity.Lab;
import com.college.selection.entity.Mentor;
import com.college.selection.entity.Offer;
import com.college.selection.entity.Publicity;
import com.college.selection.entity.SysUser;
import com.college.selection.entity.TransferRequest;
import com.college.selection.mapper.AdminMapper;
import com.college.selection.mapper.LabMapper;
import com.college.selection.mapper.MentorMapper;
import com.college.selection.mapper.NotificationMapper;
import com.college.selection.mapper.OfferMapper;
import com.college.selection.mapper.OperationLogMapper;
import com.college.selection.mapper.SysUserMapper;
import com.college.selection.mapper.TransferRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨室转组：学生已确认录取后对去向不满意，自己申请转到别的实训室的某位导师；
 * 目标导师线下约谈后接收（原去向作废，直接成为新导师的已确认录取）或拒绝。
 *
 * 只在流程图第 7 个环节「统筹调剂」开放，与管理员 ①② 调剂同一个窗口。
 */
@Service
public class TransferService {

    /** 统筹调剂对应流程图第 7 个环节 */
    private static final int STAGE_ADJUST = 7;

    private final TransferRequestMapper transferMapper;
    private final OfferMapper offerMapper;
    private final MentorMapper mentorMapper;
    private final LabMapper labMapper;
    private final SysUserMapper userMapper;
    private final StageService stageService;
    private final AdminMapper adminMapper;
    private final OperationLogMapper logMapper;
    private final NotificationMapper notificationMapper;

    public TransferService(TransferRequestMapper transferMapper,
                           OfferMapper offerMapper,
                           MentorMapper mentorMapper,
                           LabMapper labMapper,
                           SysUserMapper userMapper,
                           StageService stageService,
                           AdminMapper adminMapper,
                           OperationLogMapper logMapper,
                           NotificationMapper notificationMapper) {
        this.transferMapper = transferMapper;
        this.offerMapper = offerMapper;
        this.mentorMapper = mentorMapper;
        this.labMapper = labMapper;
        this.userMapper = userMapper;
        this.stageService = stageService;
        this.adminMapper = adminMapper;
        this.logMapper = logMapper;
        this.notificationMapper = notificationMapper;
    }

    public List<TransferRequest> listForStudent(Long studentId, Integer round) {
        return transferMapper.findByStudent(studentId, round);
    }

    /** 学生提交申请：必须本轮已确认录取，且目标导师还有名额 */
    @Transactional
    public TransferRequest submit(Long studentId, Integer round, TransferReq req) {
        stageService.assertOpen(round, STAGE_ADJUST);
        assertNotPublished(round);

        if (req == null || req.targetLabId == null || req.targetMentorId == null) {
            throw new BizException(2007, "必须选择目标实训室和导师");
        }
        String reason = req.reason == null ? "" : req.reason.trim();
        if (reason.isEmpty()) {
            throw new BizException(2007, "请写明申请转组的原因，导师约谈时要看");
        }
        if (reason.length() > 200) {
            throw new BizException(2007, "申请原因最多 200 字");
        }

        Offer current = offerMapper.findConfirmed(studentId, round);
        if (current == null) {
            throw new BizException(2007, "你本轮还没有已确认的录取去向，不能申请转组");
        }
        if (current.labId.equals(req.targetLabId)) {
            throw new BizException(2007, "目标实训室和现在的实训室相同，如果要换导师请走本室内部调剂");
        }

        Mentor target = mentorMapper.findById(req.targetMentorId);
        if (target == null) {
            throw new BizException(2007, "目标导师不存在：" + req.targetMentorId);
        }
        if (!target.labId.equals(req.targetLabId)) {
            throw new BizException(2007, "所选导师不属于该实训室，请重新选择");
        }
        if (transferMapper.findPendingByStudent(studentId, round) != null) {
            throw new BizException(2007, "你已经有一条待处理的转组申请，请等导师处理或先撤销");
        }

        int quota = target.quota == null ? 0 : target.quota;
        int used = offerMapper.countConfirmed(target.id, round);
        if (used >= quota) {
            throw new BizException(ErrorCode.QUOTA_FULL,
                    String.format("%s 名额已满（%d/%d），请换一位导师", target.name, used, quota));
        }

        TransferRequest r = new TransferRequest();
        r.studentId = studentId;
        r.roundNo = round;
        r.fromOfferId = current.id;
        r.fromMentorId = current.mentorId;
        r.fromLabId = current.labId;
        r.targetLabId = target.labId;
        r.targetMentorId = target.id;
        r.reason = reason;
        r.status = "PENDING";
        transferMapper.insert(r);

        Lab from = labMapper.findById(current.labId);
        Lab to = labMapper.findById(target.labId);
        logMapper.insert(studentId, "STUDENT", "TRANSFER_APPLY", "TRANSFER", r.id,
                "申请从 " + labName(from) + " 转到 " + labName(to) + " " + target.name + "，理由：" + reason);
        return transferMapper.findById(r.id);
    }

    /** 学生撤销还没被处理的申请 */
    @Transactional
    public void cancel(Long studentId, Long id) {
        TransferRequest r = transferMapper.lockById(id);
        if (r == null) {
            throw new BizException(4041, "转组申请不存在");
        }
        if (!r.studentId.equals(studentId)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只能撤销自己的转组申请");
        }
        if (!"PENDING".equals(r.status)) {
            throw new BizException(2006, "该申请已处理，不能撤销");
        }
        // 撤销也是"改去向"的动作：只能在统筹调剂阶段、且公示前
        stageService.assertOpen(r.roundNo, STAGE_ADJUST);
        assertNotPublished(r.roundNo);
        transferMapper.updateHandled(id, "CANCELED", null, null, studentId, LocalDateTime.now());
    }

    /** 导师端：指定转给我的待处理申请 */
    public List<TransferRequest> inbox(Long teacherId, Integer round) {
        SysUser t = requireTeacher(teacherId);
        return transferMapper.findPendingForMentor(t.mentorId, round);
    }

    /**
     * 接收转组：原去向作废，学生直接变成我的已确认录取（占我的名额）。
     * 旧导师腾出的名额不再自动递补：调剂阶段已没有"学生确认"环节，
     * 递补上来的意向会卡在待确认，交给管理员在安置检查里统一处理。
     */
    @Transactional
    public TransferRequest accept(Long teacherId, Long id, String interviewNote) {
        SysUser t = requireTeacher(teacherId);
        TransferRequest r = transferMapper.lockById(id);
        if (r == null) {
            throw new BizException(4041, "转组申请不存在");
        }
        if (!r.targetMentorId.equals(t.mentorId)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "这条转组申请不是指定给你的");
        }
        if (!"PENDING".equals(r.status)) {
            throw new BizException(2006, "该申请已处理，不能重复接收");
        }
        stageService.assertOpen(r.roundNo, STAGE_ADJUST);
        assertNotPublished(r.roundNo);
        checkNote(interviewNote);
        // 加锁查到的是裸行，展示用字段（姓名/实训室名）另查一次
        TransferRequest info = transferMapper.findById(id);

        Offer old = offerMapper.lockById(r.fromOfferId);
        if (old == null || !old.studentId.equals(r.studentId) || !"CONFIRMED".equals(old.status)) {
            throw new BizException(2006, "该学生当前的录取去向已经变化，无法转组");
        }

        Integer quota = offerMapper.lockMentorQuota(t.mentorId);
        int used = offerMapper.countConfirmed(t.mentorId, r.roundNo);
        if (quota == null || used >= quota) {
            throw new BizException(ErrorCode.QUOTA_FULL,
                    String.format("你的名额已满（%d/%d），不能再接收转组学生", used, quota == null ? 0 : quota));
        }

        LocalDateTime now = LocalDateTime.now();
        String targetLab = labName(labMapper.findById(t.labId));

        // 旧去向作废留痕
        offerMapper.updateStatus(old.id, "VOID", null);
        offerMapper.setRemark(old.id, "跨室转组：已转入 " + targetLab + " " + t.realName);

        // 新去向直接确认（学生是主动申请的，且已线下约谈）
        offerMapper.deleteByStudentMentor(r.studentId, t.mentorId, r.roundNo);
        Offer created = new Offer();
        created.studentId = r.studentId;
        created.labId = t.labId;
        created.mentorId = t.mentorId;
        created.roundNo = r.roundNo;
        created.type = "ADMITTED";
        created.rankNo = offerMapper.maxAdmittedRank(t.mentorId, r.roundNo) + 1;
        created.status = "ISSUED";
        created.issuedAt = now;
        // 直接确认，没有"等待学生确认"这一步，不需要截止时间
        created.confirmDeadline = null;
        created.remark = "跨室转组：" + r.reason;
        offerMapper.insert(created);
        offerMapper.updateStatus(created.id, "CONFIRMED", now);
        offerMapper.declineOthers(r.studentId, r.roundNo, created.id);

        transferMapper.updateHandled(r.id, "ACCEPTED", trim(interviewNote), null, teacherId, now);
        logMapper.insertWithState(teacherId, "TEACHER", "TRANSFER_ACCEPT", "TRANSFER", r.id,
                "PENDING", "ACCEPTED",
                "接收 " + info.studentName + "（" + info.studentCode + "）跨室转组，从 "
                        + info.fromLabName + " " + info.fromMentorName + " 转入 " + targetLab + " " + t.realName);
        notificationMapper.insert(r.studentId, "OFFER", "转组申请已通过",
                t.realName + " 老师已接收你的转组申请，你现在的去向是「" + targetLab + " · " + t.realName
                        + "」。原「" + info.fromLabName + " · " + info.fromMentorName + "」的录取已解除"
                        + (trim(interviewNote) == null ? "。" : "。约谈记录：" + trim(interviewNote)));
        return transferMapper.findById(r.id);
    }

    /** 拒绝转组：学生保持原去向，附拒绝原因 */
    @Transactional
    public TransferRequest reject(Long teacherId, Long id, String reason, String interviewNote) {
        SysUser t = requireTeacher(teacherId);
        TransferRequest r = transferMapper.lockById(id);
        if (r == null) {
            throw new BizException(4041, "转组申请不存在");
        }
        if (!r.targetMentorId.equals(t.mentorId)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "这条转组申请不是指定给你的");
        }
        if (!"PENDING".equals(r.status)) {
            throw new BizException(2006, "该申请已处理");
        }
        // 和"接收"一致：只在统筹调剂阶段、公示前处理
        stageService.assertOpen(r.roundNo, STAGE_ADJUST);
        assertNotPublished(r.roundNo);
        if (reason != null && reason.length() > 200) {
            throw new BizException(2007, "拒绝原因最多 200 字");
        }
        checkNote(interviewNote);

        transferMapper.updateHandled(r.id, "REJECTED", trim(interviewNote), trim(reason),
                teacherId, LocalDateTime.now());
        TransferRequest info = transferMapper.findById(id);
        logMapper.insertWithState(teacherId, "TEACHER", "TRANSFER_REJECT", "TRANSFER", r.id,
                "PENDING", "REJECTED",
                "拒绝 " + info.studentName + "（" + info.studentCode + "）的跨室转组"
                        + (trim(reason) == null ? "" : "，原因：" + trim(reason)));
        notificationMapper.insert(r.studentId, "OFFER", "转组申请未通过",
                t.realName + " 老师未接收你的转组申请，你仍保留原去向「" + info.fromLabName + " · "
                        + info.fromMentorName + "」"
                        + (trim(reason) == null ? "。" : "。原因：" + trim(reason)));
        return transferMapper.findById(r.id);
    }

    /** 最终名单一旦公示，任何改去向的动作都要停 */
    private void assertNotPublished(Integer round) {
        Publicity p = adminMapper.latestPublicity(round);
        if (p != null && ("PUBLISHED".equals(p.status) || "ENDED".equals(p.status))) {
            throw new BizException(2010, "最终名单已经公示，去向已锁定，不能再申请或处理转组");
        }
    }

    private void checkNote(String note) {
        if (note != null && note.length() > 200) {
            throw new BizException(2007, "约谈记录最多 200 字");
        }
    }

    private String trim(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }

    private String labName(Lab lab) {
        return lab == null ? "实训室" : lab.name;
    }

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
}