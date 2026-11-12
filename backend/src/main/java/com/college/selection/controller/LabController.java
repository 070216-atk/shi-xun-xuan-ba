package com.college.selection.controller;

import com.college.selection.common.Result;
import com.college.selection.entity.Lab;
import com.college.selection.service.LabService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LabController {

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    /** 实训室列表，round 默认第 1 轮 */
    @GetMapping("/labs")
    public Result<List<Lab>> list(@RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(labService.list(round));
    }

    /** 实训室详情：带导师列表和每位导师的名额 */
    @GetMapping("/labs/{id}")
    public Result<Lab> detail(@PathVariable Long id,
                              @RequestParam(defaultValue = "1") Integer round) {
        return Result.ok(labService.detail(id, round));
    }
}
