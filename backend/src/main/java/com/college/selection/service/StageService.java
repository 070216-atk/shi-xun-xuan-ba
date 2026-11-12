package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.entity.Stage;
import com.college.selection.mapper.StageMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class StageService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StageMapper stageMapper;

    public StageService(StageMapper stageMapper) {
        this.stageMapper = stageMapper;
    }

    /**
     * 返回某轮全部阶段，并算出每个阶段"现在开不开、是不是当前阶段、还剩多少秒"。
     * ⚠️ 时间判断只在这里做，用的是服务器时间；
     * 前端拿 remainingSeconds 只用于显示倒计时，不能作为放行依据。
     */
    public List<Stage> list(Integer round) {
        LocalDateTime now = LocalDateTime.now();
        List<Stage> stages = stageMapper.findByRound(round);

        for (Stage s : stages) {
            if (now.isBefore(s.startAt)) {
                s.open = false;
                s.isCurrent = false;
                s.remainingSeconds = null;
            } else if (now.isAfter(s.endAt)) {
                s.open = false;
                s.isCurrent = false;
                s.remainingSeconds = 0L;
            } else {
                s.open = true;
                s.isCurrent = true;
                s.remainingSeconds = Duration.between(now, s.endAt).getSeconds();
            }
        }
        return stages;
    }

    /** 当前所处阶段（窗口包含现在的那个），没有则返回 null */
    public Stage current(Integer round) {
        for (Stage s : list(round)) {
            if (Boolean.TRUE.equals(s.isCurrent)) {
                return s;
            }
        }
        return null;
    }

    /** 某个环节此刻是否开放（不抛异常，给"分情况处理"的地方用） */
    public boolean isOpen(Integer round, Integer stageNo) {
        LocalDateTime now = LocalDateTime.now();
        for (Stage s : stageMapper.findByRound(round)) {
            if (s.stageNo.equals(stageNo)) {
                return !now.isBefore(s.startAt) && !now.isAfter(s.endAt);
            }
        }
        return false;
    }

    /**
     * 校验某个环节此刻是否开放，不开放直接抛 2001。
     * 所有"学生能不能提交/确认"的判断都要经过这里，不要在别处重复写时间比较。
     */
    public void assertOpen(Integer round, Integer stageNo) {
        LocalDateTime now = LocalDateTime.now();
        for (Stage s : stageMapper.findByRound(round)) {
            if (!s.stageNo.equals(stageNo)) {
                continue;
            }
            if (now.isBefore(s.startAt) || now.isAfter(s.endAt)) {
                throw new BizException(ErrorCode.OUT_OF_WINDOW,
                        String.format("当前不在「%s」的开放时间内（%s 至 %s）",
                                s.name, s.startAt.format(FMT), s.endAt.format(FMT)));
            }
            return;
        }
        throw new BizException(ErrorCode.OUT_OF_WINDOW,
                "第 " + round + " 轮的第 " + stageNo + " 阶段还没有配置时间窗");
    }

    /**
     * 多个环节任一开放即放行（如意向填报 3 与分轮补录 6 都允许学生报名）。
     * 全都不开放时，报第一个已配置环节的时间窗。
     */
    public void assertAnyOpen(Integer round, Integer... stageNos) {
        LocalDateTime now = LocalDateTime.now();
        List<Stage> stages = stageMapper.findByRound(round);
        String firstError = null;

        for (Integer stageNo : stageNos) {
            Stage matched = null;
            for (Stage s : stages) {
                if (s.stageNo.equals(stageNo)) {
                    matched = s;
                    break;
                }
            }
            if (matched == null) {
                if (firstError == null) {
                    firstError = "第 " + round + " 轮的第 " + stageNo + " 阶段还没有配置时间窗";
                }
                continue;
            }
            if (!now.isBefore(matched.startAt) && !now.isAfter(matched.endAt)) {
                return;
            }
            if (firstError == null) {
                firstError = String.format("当前不在「%s」的开放时间内（%s 至 %s）",
                        matched.name, matched.startAt.format(FMT), matched.endAt.format(FMT));
            }
        }

        throw new BizException(ErrorCode.OUT_OF_WINDOW, firstError);
    }

    /** 取某阶段的结束时间，没配置返回 null（用在 offer 的确认截止时间上） */
    public LocalDateTime getEndAt(Integer round, Integer stageNo) {        for (Stage s : stageMapper.findByRound(round)) {
            if (s.stageNo.equals(stageNo)) {
                return s.endAt;
            }
        }
        return null;
    }
}
