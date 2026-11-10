package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.entity.Stage;
import com.college.selection.service.StageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StageController {

    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    /** 8 个阶段的时间轴，带 open / isCurrent / remainingSeconds */
    @GetMapping("/stages")
    public Result<List<Stage>> list(@RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(stageService.list(round));
    }
}
