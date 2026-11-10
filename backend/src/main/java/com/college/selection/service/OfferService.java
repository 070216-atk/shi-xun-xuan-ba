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

    private final OfferMapper offerMapper;

    public OfferService(OfferMapper offerMapper) {
        this.offerMapper = offerMapper;
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
    @Transactional
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
        if (offer.confirmDeadline != null && now.isAfter(offer.confirmDeadline)) {
            offerMapper.updateStatus(offer.id, "EXPIRED", null);
            throw new BizException(2006, "已超过确认截止时间，视为放弃");
        }

        Integer quota = offerMapper.lockMentorQuota(offer.mentorId);
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
