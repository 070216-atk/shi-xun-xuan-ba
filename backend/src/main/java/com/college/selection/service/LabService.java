package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.entity.Lab;
import com.college.selection.mapper.LabMapper;
import com.college.selection.mapper.MentorMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LabService {

    private final LabMapper labMapper;
    private final MentorMapper mentorMapper;

    public LabService(LabMapper labMapper, MentorMapper mentorMapper) {
        this.labMapper = labMapper;
        this.mentorMapper = mentorMapper;
    }

    /** 列表：只带汇总信息，不带导师明细 */
    public List<Lab> list(Integer round) {
        List<Lab> labs = labMapper.findAll();
        for (Lab lab : labs) {
            lab.applied = labMapper.countApplied(lab.id, round);
            lab.mentors = null;      // 见 JacksonConfig：null 字段不会输出
        }
        return labs;
    }

    /** 详情：带导师列表（含每位导师的名额） */
    public Lab detail(Long id, Integer round) {
        Lab lab = labMapper.findById(id);
        if (lab == null) {
            throw new BizException(4040, "实训室不存在");
        }
        lab.applied = labMapper.countApplied(id, round);
        lab.mentors = mentorMapper.findByLabId(id);
        return lab;
    }
}
