-- ============================================================
--  学生实训室进组选拔系统 · 数据库结构
--  MySQL 8.0 / InnoDB / utf8mb4
--  配套文档：framework/2.architecture/架构设计.md
--            framework/4.api/接口设计.md
-- ============================================================
--
--  【关键设计决策】
--  1. 撮合粒度：意向与名额都挂在【导师】上。
--     依据：真实宣讲数据里 404 室明确"高璇 14 人、张慧斌 3 人"，
--     说明名额是按导师分配的，不是按实训室平均分。
--     lab.quota 只作汇总展示用，等于该室各导师名额之和。
--  2. offer 表唯一键 (student_id, round, mentor_id)：
--     保证"一个学生一轮内对一个导师最多一个录取意向"。
--     接口因此改为 /student/offers/{offerId}/confirm，用 offer 主键定位，彻底避免歧义。
--  3. 三个平行意向必须来自三个不同实训室（application_item 的唯一键约束），
--     否则同一室多条意向会在 offer 唯一键上冲突。
--  4. 时间窗全部存 DATETIME，判断只在服务端做（架构设计 §4.1）。
--  5. 并发防超录：确认名额时对 lab 行执行 SELECT ... FOR UPDATE。
-- ============================================================

CREATE DATABASE IF NOT EXISTS lab_selection
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE lab_selection;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS interview_invite;
DROP TABLE IF EXISTS notice;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS publicity;
DROP TABLE IF EXISTS offer;
DROP TABLE IF EXISTS interview_score;
DROP TABLE IF EXISTS application_item;
DROP TABLE IF EXISTS application;
DROP TABLE IF EXISTS stage;
DROP TABLE IF EXISTS selection_round;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS mentor;
DROP TABLE IF EXISTS lab;

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 1. 实训室
-- ------------------------------------------------------------
CREATE TABLE lab (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code        VARCHAR(16)     NOT NULL COMMENT '展示用编号，如 L1',
  name        VARCHAR(64)     NOT NULL COMMENT '实训室名称',
  direction   VARCHAR(500)    DEFAULT NULL COMMENT '项目方向',
  training    VARCHAR(500)    DEFAULT NULL COMMENT '培养安排',
  assessment  VARCHAR(500)    DEFAULT NULL COMMENT '考核要求',
  location    VARCHAR(128)    DEFAULT NULL COMMENT '地点',
  contact     VARCHAR(128)    DEFAULT NULL COMMENT '联系人/电话',
  open_day    VARCHAR(128)    DEFAULT NULL COMMENT '开放日安排（展示文案）',
  quota       SMALLINT        NOT NULL DEFAULT 0 COMMENT '总名额（= 各导师名额之和，仅展示用）',
  status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1开放 0停用',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lab_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实训室';

-- ------------------------------------------------------------
-- 2. 导师档案（业务实体，不等同于登录账号）
-- ------------------------------------------------------------
CREATE TABLE mentor (
  id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  lab_id   BIGINT UNSIGNED NOT NULL,
  name     VARCHAR(32)     NOT NULL,
  title    VARCHAR(32)     DEFAULT NULL COMMENT '职称',
  intro    VARCHAR(500)    DEFAULT NULL COMMENT '研究方向/简介',
  quota    SMALLINT        NOT NULL DEFAULT 0 COMMENT '该导师招收名额（名额的真实归属）',
  sort_no  SMALLINT        NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_mentor_lab (lab_id),
  CONSTRAINT fk_mentor_lab FOREIGN KEY (lab_id) REFERENCES lab (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导师';

-- ------------------------------------------------------------
-- 3. 用户（学生 / 导师 / 管理员）
-- ------------------------------------------------------------
CREATE TABLE sys_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)     NOT NULL COMMENT '登录名',
  password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希，禁止存明文',
  role          ENUM('STUDENT','TEACHER','ADMIN') NOT NULL,
  code          VARCHAR(32)     DEFAULT NULL COMMENT '学号 / 工号',
  real_name     VARCHAR(32)     NOT NULL,
  class_name    VARCHAR(64)     DEFAULT NULL COMMENT '班级（学生）',
  lab_id        BIGINT UNSIGNED DEFAULT NULL COMMENT '导师所属实训室',
  mentor_id     BIGINT UNSIGNED DEFAULT NULL COMMENT '关联导师档案',
  phone         VARCHAR(20)     DEFAULT NULL,
  status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_code (code),
  KEY idx_user_role (role),
  KEY idx_user_lab (lab_id),
  CONSTRAINT fk_user_lab    FOREIGN KEY (lab_id)    REFERENCES lab (id),
  CONSTRAINT fk_user_mentor FOREIGN KEY (mentor_id) REFERENCES mentor (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号';

-- ------------------------------------------------------------
-- 4. 选拔轮次（首轮 / 第二轮 / 第三轮）
-- ------------------------------------------------------------
CREATE TABLE selection_round (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  round_no   TINYINT         NOT NULL COMMENT '1首轮 2第二轮 3第三轮',
  status     ENUM('PLANNING','OPEN','CLOSED') NOT NULL DEFAULT 'PLANNING',
  opened_at  DATETIME        DEFAULT NULL,
  closed_at  DATETIME        DEFAULT NULL,
  remark     VARCHAR(255)    DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_round_no (round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选拔轮次';

-- ------------------------------------------------------------
-- 5. 流程阶段与时间窗（8 个环节 × 轮次）
-- ------------------------------------------------------------
CREATE TABLE stage (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  round_no    TINYINT         NOT NULL DEFAULT 1 COMMENT '所属轮次',
  stage_no    TINYINT         NOT NULL COMMENT '1-8，对应流程图 8 个环节',
  name        VARCHAR(64)     NOT NULL,
  start_at    DATETIME        NOT NULL COMMENT '开放时间（服务器时间判定）',
  end_at      DATETIME        NOT NULL COMMENT '截止时间',
  description VARCHAR(500)    DEFAULT NULL,
  is_open     TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stage (round_no, stage_no),
  KEY idx_stage_window (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程阶段时间窗';

-- ------------------------------------------------------------
-- 6. 意向填报（一轮一条主表）
-- ------------------------------------------------------------
CREATE TABLE application (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  student_id     BIGINT UNSIGNED NOT NULL,
  round_no       TINYINT         NOT NULL DEFAULT 1,
  adjust_in_lab  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '服从实训室内部调剂',
  adjust_cross   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '服从全院跨实训室调剂',
  status         ENUM('DRAFT','SUBMITTED') NOT NULL DEFAULT 'DRAFT',
  submitted_at   DATETIME        DEFAULT NULL,
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_student_round (student_id, round_no),
  KEY idx_app_round (round_no),
  CONSTRAINT fk_app_student FOREIGN KEY (student_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意向填报主表';

-- ------------------------------------------------------------
-- 7. 意向明细（最多 3 条平行意向，不分先后）
-- ------------------------------------------------------------
CREATE TABLE application_item (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  application_id BIGINT UNSIGNED NOT NULL,
  lab_id         BIGINT UNSIGNED NOT NULL,
  mentor_id      BIGINT UNSIGNED NOT NULL COMMENT '意向导师（名额在导师上，因此必填）',
  sort_no        TINYINT         NOT NULL DEFAULT 1 COMMENT '1-3，仅作展示序号，不代表志愿先后',
  PRIMARY KEY (id),
  -- 只限制"同一位导师不能重复"，允许同一实训室填多位导师（3 个平行意向可以都投同一个室）
  UNIQUE KEY uk_item_app_mentor (application_id, mentor_id) COMMENT '同一次填报内导师不可重复',
  KEY idx_item_lab (lab_id),
  CONSTRAINT fk_item_app    FOREIGN KEY (application_id) REFERENCES application (id) ON DELETE CASCADE,
  CONSTRAINT fk_item_lab    FOREIGN KEY (lab_id)         REFERENCES lab (id),
  CONSTRAINT fk_item_mentor FOREIGN KEY (mentor_id)      REFERENCES mentor (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意向明细（平行志愿）';

-- ------------------------------------------------------------
-- 8. 面试评分（四维度，1-5 分）
-- ------------------------------------------------------------
CREATE TABLE interview_score (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  student_id   BIGINT UNSIGNED NOT NULL,
  lab_id       BIGINT UNSIGNED NOT NULL,
  round_no     TINYINT         NOT NULL DEFAULT 1,
  mentor_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '评分人对应的导师档案',
  score_attitude TINYINT       NOT NULL COMMENT '学习态度 1-5',
  score_logic    TINYINT       NOT NULL COMMENT '逻辑思维 1-5',
  score_coding   TINYINT       NOT NULL COMMENT '编程基础 1-5',
  score_time     TINYINT       NOT NULL COMMENT '时间投入 1-5',
  total        DECIMAL(4,1)    GENERATED ALWAYS AS ((score_attitude + score_logic + score_coding + score_time) / 4) STORED COMMENT '四维均分',
  comment      VARCHAR(500)    DEFAULT NULL COMMENT '评语',
  showcase     VARCHAR(1000)   DEFAULT NULL COMMENT '学生面试时展示的项目/作品/经历',
  scored_by    BIGINT UNSIGNED NOT NULL COMMENT '评分人 sys_user.id',
  scored_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_score_once (student_id, lab_id, round_no) COMMENT '同一室同一轮只保留一条（多人评分可改为主键加 scored_by）',
  KEY idx_score_lab (lab_id, round_no),
  CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES sys_user (id),
  CONSTRAINT fk_score_lab     FOREIGN KEY (lab_id)     REFERENCES lab (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试评分';

-- ------------------------------------------------------------
-- 9. 录取意向 / 递补 / 调剂候选（三份名单共用一张表）
-- ------------------------------------------------------------
CREATE TABLE offer (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  student_id       BIGINT UNSIGNED NOT NULL,
  lab_id           BIGINT UNSIGNED NOT NULL,
  mentor_id        BIGINT UNSIGNED NOT NULL,
  target_mentor_id BIGINT UNSIGNED DEFAULT NULL COMMENT '内部调剂：导师指定的接收方导师',
  round_no         TINYINT         NOT NULL DEFAULT 1,
  type             ENUM('ADMITTED','WAITLIST','ADJUST_CANDIDATE') NOT NULL
                   COMMENT '拟录取 / 递补 / 内部调剂候选',
  rank_no          SMALLINT        DEFAULT NULL COMMENT '递补顺位或候选序号，从 1 开始',
  status           ENUM('ISSUED','CONFIRMED','DECLINED','EXPIRED','VOID') NOT NULL DEFAULT 'ISSUED'
                   COMMENT '已下发 / 已确认 / 主动放弃 / 逾期放弃 / 作废',
  issued_at        DATETIME        DEFAULT NULL,
  confirm_deadline DATETIME        DEFAULT NULL COMMENT '确认截止时间（递补产生时单独计算）',
  confirmed_at     DATETIME        DEFAULT NULL,
  remark           VARCHAR(255)    DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_offer_stu_round_mentor (student_id, round_no, mentor_id) COMMENT '一个学生一轮内对一个导师最多一个意向',
  KEY idx_offer_lab_type (lab_id, round_no, type, rank_no),
  KEY idx_offer_student  (student_id, round_no),
  CONSTRAINT fk_offer_student FOREIGN KEY (student_id) REFERENCES sys_user (id),
  CONSTRAINT fk_offer_lab     FOREIGN KEY (lab_id)     REFERENCES lab (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='录取意向/递补/调剂候选';

-- ------------------------------------------------------------
-- 10. 公示
-- ------------------------------------------------------------
CREATE TABLE publicity (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  round_no         TINYINT         NOT NULL DEFAULT 1,
  status           ENUM('DRAFT','PUBLISHED','ENDED') NOT NULL DEFAULT 'DRAFT',
  start_at         DATETIME        DEFAULT NULL COMMENT '公示开始（公示期 1 天）',
  end_at           DATETIME        DEFAULT NULL COMMENT '公示结束',
  objection_count  SMALLINT        NOT NULL DEFAULT 0 COMMENT '未处理异议数，>0 时不可结束公示',
  published_by     BIGINT UNSIGNED DEFAULT NULL,
  published_at     DATETIME        DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_pub_round (round_no, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结果公示';

-- ------------------------------------------------------------
-- 11. 操作日志（关键操作留痕）
-- ------------------------------------------------------------
CREATE TABLE operation_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  operator_id   BIGINT UNSIGNED DEFAULT NULL,
  operator_role VARCHAR(16)     DEFAULT NULL,
  action        VARCHAR(64)     NOT NULL COMMENT 'CONFIRM / DECLINE / ADJUST_INTERNAL / PUBLISH ...',
  target_type   VARCHAR(32)     DEFAULT NULL COMMENT 'OFFER / APPLICATION / PUBLICITY / STAGE',
  target_id     BIGINT UNSIGNED DEFAULT NULL,
  before_state  VARCHAR(64)     DEFAULT NULL,
  after_state   VARCHAR(64)     DEFAULT NULL,
  remark        VARCHAR(500)    DEFAULT NULL,
  ip            VARCHAR(64)     DEFAULT NULL,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_log_operator (operator_id, created_at),
  KEY idx_log_target   (target_type, target_id),
  KEY idx_log_action   (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- ------------------------------------------------------------
-- 12. 站内通知
-- ------------------------------------------------------------
CREATE TABLE notification (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  type       VARCHAR(32)     NOT NULL COMMENT 'APPLY_OPEN / DEADLINE / OFFER / WAITLIST / PUBLICITY',
  title      VARCHAR(128)    NOT NULL,
  content    VARCHAR(1000)   DEFAULT NULL,
  is_read    TINYINT(1)      NOT NULL DEFAULT 0,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notify_user (user_id, is_read, created_at),
  CONSTRAINT fk_notify_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

-- ------------------------------------------------------------
-- 13. 通知公告
-- ------------------------------------------------------------
CREATE TABLE notice (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title        VARCHAR(200)    NOT NULL,
  content      VARCHAR(2000)   DEFAULT NULL,
  publisher_id BIGINT UNSIGNED DEFAULT NULL COMMENT '发布人 sys_user.id',
  is_top       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1 置顶',
  status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1 发布 0 下线',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notice (status, is_top, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知公告';

-- ------------------------------------------------------------
-- 14. 面试入围（导师先初选，只有入围者才面试）
-- ------------------------------------------------------------
CREATE TABLE interview_invite (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  round_no    TINYINT         NOT NULL DEFAULT 1,
  lab_id      BIGINT UNSIGNED NOT NULL,
  mentor_id   BIGINT UNSIGNED NOT NULL COMMENT '发出邀请的导师',
  student_id  BIGINT UNSIGNED NOT NULL,
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invite (round_no, mentor_id, student_id) COMMENT '同一导师对同一学生只邀请一次',
  KEY idx_invite_lab (round_no, lab_id, student_id),
  CONSTRAINT fk_invite_student FOREIGN KEY (student_id) REFERENCES sys_user (id),
  CONSTRAINT fk_invite_lab     FOREIGN KEY (lab_id)     REFERENCES lab (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试入围（导师初选名单）';

-- ============================================================
--  学生状态枚举（不建表，由 application + offer + publicity 推导）
--  对应 PRD / 架构设计的状态机：
--    NOT_APPLIED      未填报   application 不存在
--    APPLIED          已填报   application.SUBMITTED
--    INTERVIEWED      面试完成 interview_score 存在
--    OFFERED          拟录取   offer.ADMITTED + ISSUED
--    CONFIRMED        已确认   offer.ADMITTED + CONFIRMED
--    DECLINED         放弃     offer.DECLINED / EXPIRED
--    WAITLISTED       递补中   offer.WAITLIST
--    ADJUSTING        调剂中   offer.ADJUST_CANDIDATE
--    NOT_ADMITTED     未录取   该轮结束仍无 CONFIRMED
--    ADMITTED         已录取   publicity ENDED 后仍 CONFIRMED
--  建议在 service 层集中实现，controller 不得直接写状态。
-- ============================================================
