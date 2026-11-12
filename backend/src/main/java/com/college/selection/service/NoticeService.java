package com.college.selection.service;

import com.college.selection.common.BizException;
import com.college.selection.common.ErrorCode;
import com.college.selection.dto.NoticeReq;
import com.college.selection.entity.Notice;
import com.college.selection.entity.SysUser;
import com.college.selection.mapper.NoticeMapper;
import com.college.selection.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoticeService {

    private final NoticeMapper noticeMapper;
    private final SysUserMapper userMapper;

    public NoticeService(NoticeMapper noticeMapper, SysUserMapper userMapper) {
        this.noticeMapper = noticeMapper;
        this.userMapper = userMapper;
    }

    /** 前台 / 首页用，只看已发布的 */
    public List<Notice> published() {
        return noticeMapper.findPublished();
    }

    public List<Notice> all(Long adminId) {
        requireAdmin(adminId);
        return noticeMapper.findAll();
    }

    public Notice save(Long adminId, NoticeReq req) {
        requireAdmin(adminId);
        if (req.title == null || req.title.isBlank()) {
            throw new BizException(2013, "标题不能为空");
        }
        Notice n = new Notice();
        n.title = req.title.trim();
        n.content = req.content == null ? "" : req.content;
        n.isTop = req.isTop != null && req.isTop;
        n.status = req.status == null ? 1 : req.status;

        if (req.id == null) {
            n.publisherId = adminId;
            noticeMapper.insert(n);
        } else {
            n.id = req.id;
            if (noticeMapper.findById(req.id) == null) {
                throw new BizException(2013, "公告不存在：" + req.id);
            }
            noticeMapper.update(n);
        }
        return noticeMapper.findById(n.id);
    }

    public void delete(Long adminId, Long id) {
        requireAdmin(adminId);
        noticeMapper.delete(id);
    }

    private void requireAdmin(Long adminId) {
        SysUser u = userMapper.findById(adminId);
        if (u == null || !"ADMIN".equals(u.role)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "只有学院管理员可以维护公告");
        }
    }
}
