package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.entity.Offer;
import com.college.selection.mapper.OfferMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OfferService {

    /** 拟录取与学生确认对应流程图第 5 个环节 */
    private static final int STAGE_CONFIRM = 5;
    /** 分轮补录：补录轮里发拟录取和确认学生都在这一环节完成 */
    private static final int STAGE_REFILL = 6;

    private final OfferMapper offerMapper;
    private final StageService stageService;

    public OfferService(OfferMapper offerMapper, StageService stageService) {
        this.offerMapper = offerMapper;
        this.stageService = stageService;
    }

    /** 本人某轮的全部意向，按 拟录取 → 递补 → 调剂候选 排序 */
    public List<Offer> list(Long studentId, Integer round) {
        List<Offer> offers = offerMapper.findByStudent(studentId, round);
        LocalDateTime now = LocalDateTime.now();
        for (Offer o : offers) {
            if (o.confirmDeadline != null && "ISSUED".equals(o.status)) {
                long sec = Duration.between(now, o.confirmDeadline).getSeconds();
                o.remainingSeconds = Math.max(sec, 0);
            }
        }
        return offers;
    }

    /**
     * 确认唯一去向。
     *
     * 并发要点（顺序不能变，否则可能死锁）：
     *   1) 先锁 offer 行   → 保证同一个 offer 不被重复确认
     *   2) 再锁 mentor 行  → 保证同一个导师的名额不被并发超录
     * 两个学生确认不同 offer 但同一导师时，会在第 2 步排队。
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> confirm(Long studentId, Long offerId) {
        Offer offer = offerMapper.lockById(offerId);
        if (offer == null) {
            throw new BizException(4041, "录取意向不存在");
        }
        if (!offer.studentId.equals(studentId)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只能确认自己的录取意向");
        }
        if (!"ADMITTED".equals(offer.type)) {
            throw new BizException(2006, "只有拟录取意向可以确认");
        }
        if (!"ISSUED".equals(offer.status)) {
            throw new BizException(2006, "该意向当前状态为 " + offer.status + "，不可确认");
        }

        LocalDateTime now = LocalDateTime.now();
        // 确认窗口以"阶段 5 或分轮补录是否开放"为准。
        // 不用 offer.confirm_deadline 判断：那个值是发名单时算的，那时阶段 5 可能还没配置。
        stageService.assertAnyOpen(offer.roundNo, STAGE_CONFIRM, STAGE_REFILL);

        Integer quota = offerMapper.lockMentorQuota(offer.mentorId);
        // 这里能安全地用普通 COUNT，是因为本方法把隔离级别降到了 READ COMMITTED：
        // 在 REPEATABLE READ 下，普通 SELECT 读的是事务开始时的快照，
        // 等锁拿到之后仍然看到旧值（都是 0），三个人会同时认为还有空位。
        // READ COMMITTED 下每次查询都读最新已提交数据，配合上面的导师行锁即可正确串行化。
        // （不要改成 "SELECT ... FOR UPDATE" 去数：范围锁会和 lockById 的单行锁互相等待，引发死锁）
        int confirmed = offerMapper.countConfirmed(offer.mentorId, offer.roundNo);
        if (quota == null || confirmed >= quota) {
            throw new BizException(ErrorCode.QUOTA_FULL, "该导师名额已满");
        }

        offerMapper.updateStatus(offer.id, "CONFIRMED", now);
        int released = offerMapper.declineOthers(studentId, offer.roundNo, offer.id);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("confirmedOfferId", offer.id);
        data.put("mentorId", offer.mentorId);
        data.put("releasedCount", released);
        return data;
    }

    /** 最终去向（用于 /student/result） */
    public Offer result(Long studentId, Integer round) {
        return offerMapper.findConfirmed(studentId, round);
    }
}
