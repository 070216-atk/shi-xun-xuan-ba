package com.college.selection.service;

import com.college.selection.entity.Stage;
import com.college.selection.mapper.StageMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StageService {

    private final StageMapper stageMapper;

    public StageService(StageMapper stageMapper) {
        this.stageMapper = stageMapper;
    }

    /**
     * 返回某轮全部阶段，并算出每个阶段"现在开不开、是不是当前阶段、还剩多少秒"。
     * ⚠️ 时间判断只在这里做，而且用的是服务器时间（LocalDateTime.now()），
     * 前端拿到 remainingSeconds 只用于显示倒计时，不能作为放行依据。
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
}
