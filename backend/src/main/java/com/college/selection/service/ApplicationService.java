package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.dto.ApplicationReq;
import com.college.selection.entity.Application;
import com.college.selection.entity.ApplicationItem;
import com.college.selection.entity.Mentor;
import com.college.selection.mapper.ApplicationMapper;
import com.college.selection.mapper.MentorMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ApplicationService {

    /** 意向填报对应流程图第 3 个环节 */
    private static final int STAGE_APPLY = 3;
    private static final int MAX_ITEMS = 3;

    private final ApplicationMapper appMapper;
    private final MentorMapper mentorMapper;
    private final StageService stageService;

    public ApplicationService(ApplicationMapper appMapper,
                              MentorMapper mentorMapper,
                              StageService stageService) {
        this.appMapper = appMapper;
        this.mentorMapper = mentorMapper;
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
     * 提交或修改意向（同一轮重复提交 = 覆盖）。
     * 校验顺序：时间窗 → 调剂开关 → 意向数量 → 重复 → 导师存在性 → 写入
     */
    @Transactional
    public void save(Long studentId, ApplicationReq req) {
        int round = req.round == null ? 1 : req.round;

        // 1. 服务端时间窗，前端倒计时不作数
        stageService.assertOpen(round, STAGE_APPLY);

        // 2. 两个调剂意愿必须都选（是 / 否），不能留空
        if (req.adjustInLab == null || req.adjustCross == null) {
            throw new BizException(ErrorCode.TOO_MANY_INTENTS, "必须选择是否服从实训室内部调剂和全院跨实训室调剂");
        }

        // 3. 意向数量 1–3
        List<ApplicationReq.Item> items = req.items;
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.TOO_MANY_INTENTS, "至少填报 1 个意向");
        }
        if (items.size() > MAX_ITEMS) {
            throw new BizException(ErrorCode.TOO_MANY_INTENTS, "最多填报 " + MAX_ITEMS + " 个平行意向");
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

        // 5. 落库（有则更新，无则新建）
        Application existing = appMapper.find(studentId, round);
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            Application app = new Application();
            app.studentId = studentId;
            app.roundNo = round;
            app.adjustInLab = req.adjustInLab;
            app.adjustCross = req.adjustCross;
            app.status = "SUBMITTED";
            app.submittedAt = now;
            appMapper.insert(app);
            existing = app;
        } else {
            existing.adjustInLab = req.adjustInLab;
            existing.adjustCross = req.adjustCross;
            existing.status = "SUBMITTED";
            existing.submittedAt = now;
            appMapper.update(existing);
            appMapper.deleteItems(existing.id);
        }

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
