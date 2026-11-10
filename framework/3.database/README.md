# 数据库使用说明

> 对应 [schema.sql](schema.sql) 与 [seed.sql](seed.sql)
> 目标环境：MySQL 8.0（本机已安装：`C:\Program Files\MySQL\MySQL Server 8.0`）

## 1. 导入

### 命令行（推荐，可复现）

```powershell
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
& $mysql -u root -p --default-character-set=utf8mb4 -e "source E:/学校文件/大二上作业/shi-xun-xuan-ba/framework/3.database/schema.sql"
& $mysql -u root -p --default-character-set=utf8mb4 -e "source E:/学校文件/大二上作业/shi-xun-xuan-ba/framework/3.database/seed.sql"
```

> `source` 后面用**正斜杠** `/`，不要用反斜杠。

### Navicat

新建连接 → 右键「运行 SQL 文件」→ 选 `schema.sql` 执行；再执行 `seed.sql`。
**连接属性里把字符集设为 `utf8mb4`**，否则中文会乱码。

## 2. 密码占位符（必须处理）

`seed.sql` 里的 `password_hash` 是假的（`$2a$10$REPLACE_WITH_REAL_BCRYPT`），因为 SQL 里没法生成 BCrypt。

三种解法，按推荐顺序：

1. **后端启动时自动建账号**（推荐）：写一个 `CommandLineRunner`，用户表为空时用
   `new BCryptPasswordEncoder().encode("123456")` 插入，不依赖 SQL。
2. 用 Java 生成后手工替换：
   ```java
   System.out.println(new BCryptPasswordEncoder().encode("123456"));
   ```
   把输出填进 `seed.sql` 对应行，重新执行。
3. 临时方案：改成明文比较（**仅本地调试**，别提交）。

## 3. 演示用的时间窗

`seed.sql` 里的阶段时间是流程图上的历史日期（2026-10-29 起）。
如果今天跑演示，所有阶段都会判成"已截止"，流程走不动。

`seed.sql` 末尾有一段注释掉的平移脚本，取消注释后执行，就能把全部阶段整体往后挪到今天：

```sql
SET @shift = DATEDIFF(CURDATE(), '2026-10-29');
UPDATE stage
   SET start_at = DATE_ADD(start_at, INTERVAL @shift DAY),
       end_at   = DATE_ADD(end_at,   INTERVAL @shift DAY);
```

## 4. 表结构速览

| 表 | 作用 | 关键约束 |
| --- | --- | --- |
| `lab` | 实训室 + 名额 | `code` 唯一 |
| `mentor` | 导师档案 | 属于某个 lab |
| `sys_user` | 账号（三角色） | `username`、`code` 唯一 |
| `selection_round` | 轮次 1/2/3 | `round_no` 唯一 |
| `stage` | 8 阶段时间窗 | `(round_no, stage_no)` 唯一 |
| `application` | 一轮一条填报主表 | `(student_id, round_no)` 唯一 |
| `application_item` | 最多 3 条平行意向 | `(application_id, lab_id)` 唯一 |
| `interview_score` | 四维评分 | 四维均分自动生成 |
| `offer` | 拟录取/递补/调剂候选 | **`(student_id, round_no, lab_id)` 唯一** |
| `publicity` | 公示 | 异议数 >0 不能结束 |
| `operation_log` | 操作留痕 | — |
| `notification` | 站内通知 | — |

## 5. 三个容易踩的点

1. **`offer` 的唯一键是刻意的**：保证一轮内每室最多一个意向，所以接口 `/student/offers/{labId}/confirm` 才能用 `labId` 唯一定位。别随手删。
2. **名额挂在 `lab` 上**，不在 `mentor` 上。以后要按导师分名额，加 `mentor.quota` 即可。
3. **状态不建表**，由 `application` + `offer` + `publicity` 推导（枚举见 `schema.sql` 末尾注释）。推导逻辑必须集中在 service 层。

## 6. 演示时让阶段保持开放

按流程图的真实日期，所有阶段今天都已结束。演示前执行：

```sql
-- 把全部阶段整体平移到今天
SET @shift = DATEDIFF(CURDATE(), '2026-11-02');
UPDATE stage SET start_at = DATE_ADD(start_at, INTERVAL @shift DAY),
                 end_at   = DATE_ADD(end_at,   INTERVAL @shift DAY);

-- 或者只把"意向填报"打开（最常用）
UPDATE stage
   SET start_at = DATE_SUB(NOW(), INTERVAL 1 HOUR),
       end_at   = DATE_ADD(NOW(), INTERVAL 1 DAY)
 WHERE round_no = 1 AND stage_no = 3;
```

> 时间判断只在服务端做，改完库立即生效，不用重启服务。