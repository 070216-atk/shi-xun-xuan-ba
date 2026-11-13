package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.dto.ApplicationReq;
import com.college.selection.entity.Application;
import com.college.selection.entity.ApplicationItem;
import com.college.selection.entity.Mentor;
import com.college.selection.entity.Offer;
import com.college.selection.mapper.ApplicationMapper;
import com.college.selection.mapper.MentorMapper;
import com.college.selection.mapper.OfferMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ApplicationService {

    /** 意向填报（第 3 环节）与分轮补录（第 6 环节）都可以报名 */
    private static final int STAGE_APPLY = 3;
    private static final int STAGE_REFILL = 6;
    private static final int MAX_ITEMS = 3;

    private final ApplicationMapper appMapper;
    private final MentorMapper mentorMapper;
    private final OfferMapper offerMapper;
    private final StageService stageService;

    public ApplicationService(ApplicationMapper appMapper,
                              MentorMapper mentorMapper,
                              OfferMapper offerMapper,
                              StageService stageService) {
        this.appMapper = appMapper;
        this.mentorMapper = mentorMapper;
        this.offerMapper = offerMapper;
        this.stageService = stageService;
    }

    /** 查本人某轮意向，未填报返回 null */
    public Application get(Long studentId, Integer round) {
        Application app = appMapper.find(studentId, round);
        if (app != null) {
            app.items = appMapper.findItems(app.id);
        }
        return app;
    }

    /**
     * 提交意向（每轮只能提交一次，提交后不可修改）。
     * 校验顺序：录取终局 → 时间窗 → 是否已提交 → 调剂开关 → 意向数量 → 重复 → 导师存在性 → 写入
     */
    @Transactional
    public void save(Long studentId, ApplicationReq req) {
        int round = req.round == null ? 1 : req.round;

        // 0. 已经在前面某一轮被录取的学生不再参与后续轮次
        Offer admitted = offerMapper.findConfirmedUpTo(studentId, round);
        if (admitted != null) {
            throw new BizException(ErrorCode.TOO_MANY_INTENTS,
                    "你已在第 " + admitted.roundNo + " 轮被录取（" + admitted.labName + "），不能再填报意向");
        }

        // 1. 服务端时间窗，前端倒计时不作数（意向填报或分轮补录均放行）
        stageService.assertAnyOpen(round, STAGE_APPLY, STAGE_REFILL);

        // 2. 每轮只能提交一次，提交后锁定
        if (appMapper.find(studentId, round) != null) {
            throw new BizException(ErrorCode.TOO_MANY_INTENTS,
                    "第 " + round + " 轮意向已提交，不能重复提交或修改");
        }

        // 2. 两个调剂意愿必须都选（是 / 否），不能留空
        if (req.adjustInLab == null || req.adjustCross == null) {
            throw new BizException(ErrorCode.TOO_MANY_INTENTS, "必须选择是否服从实训室内部调剂和全院跨实训室调剂");
        }

        // 3. 意向数量：补录阶段只允许 1 个，正常填报阶段 1–3 个
        boolean refill = stageService.isOpen(round, STAGE_REFILL);
        int maxItems = refill ? 1 : MAX_ITEMS;
        List<ApplicationReq.Item> items = req.items;
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.TOO_MANY_INTENTS, "至少填报 1 个意向");
        }
        if (items.size() > maxItems) {
            throw new BizException(ErrorCode.TOO_MANY_INTENTS,
                    refill ? "分轮补录阶段只能填报 1 个导师意向"
                           : "最多填报 " + MAX_ITEMS + " 个平行意向");
        }

        // 4. 去重 + 导师校验
        //    只限制"同一位导师不能重复"；同一实训室填多位导师是允许的
        //    （例如 3 个平行意向都投 404 室的高璇、张慧斌、王鹏帆）
        Set<Long> mentorIds = new HashSet<>();
        for (ApplicationReq.Item item : items) {
            if (item == null || item.labId == null || item.mentorId == null) {
                throw new BizException(ErrorCode.TOO_MANY_INTENTS, "每个意向都要同时指定实训室和导师");
            }
            if (!mentorIds.add(item.mentorId)) {
                throw new BizException(ErrorCode.TOO_MANY_INTENTS, "同一位导师不能重复填报");
            }
            Mentor mentor = mentorMapper.findById(item.mentorId);
            if (mentor == null) {
                throw new BizException(ErrorCode.TOO_MANY_INTENTS, "导师不存在：" + item.mentorId);
            }
            if (!mentor.labId.equals(item.labId)) {
                throw new BizException(ErrorCode.TOO_MANY_INTENTS, "导师与实训室不匹配：" + mentor.name);
            }
        }

        // 5. 落库（前面已确认本轮没有提交记录，这里只做新建）
        Application existing = new Application();
        existing.studentId = studentId;
        existing.roundNo = round;
        existing.adjustInLab = req.adjustInLab;
        existing.adjustCross = req.adjustCross;
        existing.status = "SUBMITTED";
        existing.submittedAt = LocalDateTime.now();
        appMapper.insert(existing);

        List<ApplicationItem> rows = new ArrayList<>();
        int sort = 1;
        for (ApplicationReq.Item item : items) {
            ApplicationItem row = new ApplicationItem();
            row.applicationId = existing.id;
            row.labId = item.labId;
            row.mentorId = item.mentorId;
            row.sortNo = sort++;
            appMapper.insertItem(row);
            rows.add(row);
        }
        existing.items = rows;
    }
}
