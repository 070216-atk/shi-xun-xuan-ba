package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.dto.ApplicantVO;
import com.college.selection.dto.ScoreReq;
import com.college.selection.dto.TeacherListsReq;
import com.college.selection.dto.TeacherListsVO;
import com.college.selection.entity.Offer;
import com.college.selection.entity.SysUser;
import com.college.selection.mapper.OfferMapper;
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

    private final TeacherMapper teacherMapper;
    private final OfferMapper offerMapper;
    private final SysUserMapper userMapper;
    private final StageService stageService;

    public TeacherService(TeacherMapper teacherMapper,
                          OfferMapper offerMapper,
                          SysUserMapper userMapper,
                          StageService stageService) {
        this.teacherMapper = teacherMapper;
        this.offerMapper = offerMapper;
        this.userMapper = userMapper;
        this.stageService = stageService;
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
     *   1. 只能在"面试考核"阶段提交
     *   2. 拟录取人数 ≤ 该导师名额
     *   3. 每个学生都必须报过本室
     *   4. 已经确认了其他导师的学生不能再发拟录取
     *   5. 重复提交 = 覆盖本导师本轮名单
     *   6. 提交后若某学生本轮只有 1 个拟录取意向 → 自动确认（PRD R8）
     */
    @Transactional
    public void saveLists(Long teacherId, Integer round, TeacherListsReq req) {
        SysUser t = requireTeacher(teacherId);
        stageService.assertOpen(round, STAGE_INTERVIEW);

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

        LocalDateTime deadline = stageService.getEndAt(round, STAGE_CONFIRM);
        LocalDateTime now = LocalDateTime.now();

        offerMapper.deleteByMentor(t.mentorId, round);

        List<Long> allStudents = new ArrayList<>();
        allStudents.addAll(admitted);
        allStudents.addAll(waitlist);
        allStudents.addAll(adjust);
        for (Long studentId : allStudents) {
            if (teacherMapper.countAppliedToLab(studentId, t.labId, round) == 0) {
                throw new BizException(2007, "学生 " + studentId + " 没有报名本实训室");
            }
        }
        for (Long studentId : admitted) {
            if (offerMapper.countAnyConfirmed(studentId, round) > 0) {
                throw new BizException(2007, "学生 " + studentId + " 已确认了其他导师");
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

        // 只有 1 个拟录取意向的学生自动确认
        for (Offer o : inserted) {
            if ("ADMITTED".equals(o.type) && offerMapper.countAdmittedIssued(o.studentId, round) == 1) {
                offerMapper.autoConfirm(o.id);
            }
        }
    }

    private Offer insert(SysUser t, Integer round, Long studentId, String type, Integer rank,
                         LocalDateTime now, LocalDateTime deadline) {
        Offer o = new Offer();
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
