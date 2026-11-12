package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.dto.ApplicantVO;
import com.college.selection.dto.ScoreReq;
import com.college.selection.dto.TeacherListsReq;
import com.college.selection.dto.TeacherListsVO;
import com.college.selection.entity.Offer;
import com.college.selection.entity.SysUser;
import com.college.selection.mapper.ApplicationMapper;
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
import java.util.List;

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

    public TeacherService(TeacherMapper teacherMapper,
                          OfferMapper offerMapper,
                          ApplicationMapper applicationMapper,
                          SysUserMapper userMapper,
                          StageService stageService,
                          OperationLogMapper logMapper) {
        this.teacherMapper = teacherMapper;
        this.offerMapper = offerMapper;
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.stageService = stageService;
        this.logMapper = logMapper;
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
        for (ApplicantVO a : list) {
            a.interviewed = a.scoreAttitude != null;
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
        List<Long> adjust = req.adjustCandidates == null ? Collections.emptyList() : req.adjustCandidates;

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
        for (Long studentId : admitted) {
            if (offerMapper.countAnyConfirmed(studentId, round) > 0) {
                throw new BizException(2007, who(studentId) + " 已确认了其他导师，不能再发拟录取");
            }
        }

        List<Offer> inserted = new ArrayList<>();
        for (Long studentId : admitted) {
            inserted.add(insert(t, round, studentId, "ADMITTED", null, now, deadline));
        }
        for (int i = 0; i < waitlist.size(); i++) {
            inserted.add(insert(t, round, waitlist.get(i), "WAITLIST", i + 1, now, deadline));
        }
        for (int i = 0; i < adjust.size(); i++) {
            inserted.add(insert(t, round, adjust.get(i), "ADJUST_CANDIDATE", i + 1, now, deadline));
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
            offerMapper.promoteToAdmitted(next.id, stageService.getEndAt(round, STAGE_CONFIRM));
            promotedStudentId = next.studentId;
        }

        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("revokedStudentId", studentId);
        m.put("promotedStudentId", promotedStudentId);
        m.put("promotedName", promotedStudentId == null ? null : who(promotedStudentId));
        return m;
    }

    private Offer insert(SysUser t, Integer round, Long studentId, String type, Integer rank,
                         LocalDateTime now, LocalDateTime deadline) {        Offer o = new Offer();
        o.studentId = studentId;
        o.labId = t.labId;
        o.mentorId = t.mentorId;
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
