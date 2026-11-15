-- ============================================================
-- 演示数据：4 个用户 / 8 条内容 / 9 条点赞
--
-- 用法（先起好库里已有表）：
--   mysql -uroot -p < deploy/sql/seed-demo.sql
--   redis-cli DEL hot:content      # 让热榜从 DB 重建，否则可能还是旧缓存
--
-- 演示账号（密码都是 Passw0rd!2026）：
--   demo_alice / demo_bob / demo_carol / demo_dave
--
-- 两个刻意的取舍：
-- 1. 全部 INSERT IGNORE：靠唯一键（uk_username / uk_user_idem / uk_user_content）
--    保证可重复执行，不会插出重复行。
-- 2. 本脚本【不】挂进 docker-entrypoint-initdb.d：有数据的库会让
--    ContentMapperTest.cursorPagination（它假设 content 表为空）失败。
--    想看有数据的界面时手动执行这一步。
-- ============================================================

USE `community`;

-- ------------------------------------------------------------
-- 用户：password_hash 是 BCrypt("Passw0rd!2026")，与注册接口产出的格式一致
-- ------------------------------------------------------------
INSERT IGNORE INTO `user` (`username`, `password_hash`, `nickname`) VALUES
    ('demo_alice', '$2a$10$KiYuBkmar3OcHLakhitJ8.PbEDGHaP.rc2KLLBCKIt/mY.z39rpKq', 'Alice'),
    ('demo_bob',   '$2a$10$KiYuBkmar3OcHLakhitJ8.PbEDGHaP.rc2KLLBCKIt/mY.z39rpKq', 'Bob'),
    ('demo_carol', '$2a$10$KiYuBkmar3OcHLakhitJ8.PbEDGHaP.rc2KLLBCKIt/mY.z39rpKq', 'Carol'),
    ('demo_dave',  '$2a$10$KiYuBkmar3OcHLakhitJ8.PbEDGHaP.rc2KLLBCKIt/mY.z39rpKq', 'Dave');

-- ------------------------------------------------------------
-- 内容：作者 id 用子查询取，不能硬编码自增 id
--       idem_key 固定，重复执行会被 uk_user_idem 挡掉
-- ------------------------------------------------------------
INSERT IGNORE INTO `content` (`user_id`, `idem_key`, `body`, `deleted`, `like_count`, `created_at`)
SELECT u.id, 'seed-alice-0001', '游标分页比 offset 稳：深翻页时不会因为刚好有人发了新内容而出现重复条目。', 0, 0, NOW() - INTERVAL 8 MINUTE
FROM `user` u WHERE u.username = 'demo_alice';

INSERT IGNORE INTO `content` (`user_id`, `idem_key`, `body`, `deleted`, `like_count`, `created_at`)
SELECT u.id, 'seed-alice-0002', '点赞用 INSERT IGNORE 加唯一键，重复点击不会把计数加两次。', 0, 0, NOW() - INTERVAL 42 MINUTE
FROM `user` u WHERE u.username = 'demo_alice';

INSERT IGNORE INTO `content` (`user_id`, `idem_key`, `body`, `deleted`, `like_count`, `created_at`)
SELECT u.id, 'seed-bob-0001', '热榜存在 Redis ZSet 里，缓存丢了会从数据库懒重建，不用手工修数据。', 0, 0, NOW() - INTERVAL 15 MINUTE
FROM `user` u WHERE u.username = 'demo_bob';

INSERT IGNORE INTO `content` (`user_id`, `idem_key`, `body`, `deleted`, `like_count`, `created_at`)
SELECT u.id, 'seed-bob-0002', '登录接口按 IP 做了固定窗口限流，一分钟 5 次，第 6 次直接 429。', 0, 0, NOW() - INTERVAL 3 HOUR
FROM `user` u WHERE u.username = 'demo_bob';

INSERT IGNORE INTO `content` (`user_id`, `idem_key`, `body`, `deleted`, `like_count`, `created_at`)
SELECT u.id, 'seed-carol-0001', '发布接口支持 Idempotency-Key：同一份草稿重复提交只会创建一条。', 0, 0, NOW() - INTERVAL 26 MINUTE
FROM `user` u WHERE u.username = 'demo_carol';

INSERT IGNORE INTO `content` (`user_id`, `idem_key`, `body`, `deleted`, `like_count`, `created_at`)
SELECT u.id, 'seed-carol-0002', '详情缓存把空结果也缓存成 __NULL__ 标记，挡住不存在的 id 反复打库。', 0, 0, NOW() - INTERVAL 5 HOUR
FROM `user` u WHERE u.username = 'demo_carol';

INSERT IGNORE INTO `content` (`user_id`, `idem_key`, `body`, `deleted`, `like_count`, `created_at`)
SELECT u.id, 'seed-dave-0001', 'JWT 登出是把 jti 写进 Redis 黑名单，TTL 等于这张票的剩余有效期。', 0, 0, NOW() - INTERVAL 55 MINUTE
FROM `user` u WHERE u.username = 'demo_dave';

INSERT IGNORE INTO `content` (`user_id`, `idem_key`, `body`, `deleted`, `like_count`, `created_at`)
SELECT u.id, 'seed-dave-0002', 'like_count 是 INT UNSIGNED，减一必须写成 GREATEST(x-1, 0)，裸减会报 1690。', 0, 0, NOW() - INTERVAL 1 DAY
FROM `user` u WHERE u.username = 'demo_dave';

-- ------------------------------------------------------------
-- 点赞：分布不均，热榜才有区分度（3 / 2 / 2 / 1 / 1 / 1）
-- ------------------------------------------------------------
INSERT IGNORE INTO `content_like` (`user_id`, `content_id`)
SELECT u.id, c.id FROM `user` u CROSS JOIN `content` c
WHERE u.username IN ('demo_bob', 'demo_carol', 'demo_dave')
  AND c.idem_key = 'seed-alice-0001';

INSERT IGNORE INTO `content_like` (`user_id`, `content_id`)
SELECT u.id, c.id FROM `user` u CROSS JOIN `content` c
WHERE u.username IN ('demo_bob', 'demo_carol')
  AND c.idem_key = 'seed-alice-0002';

INSERT IGNORE INTO `content_like` (`user_id`, `content_id`)
SELECT u.id, c.id FROM `user` u CROSS JOIN `content` c
WHERE u.username IN ('demo_dave')
  AND c.idem_key = 'seed-bob-0001';

INSERT IGNORE INTO `content_like` (`user_id`, `content_id`)
SELECT u.id, c.id FROM `user` u CROSS JOIN `content` c
WHERE u.username IN ('demo_alice', 'demo_bob')
  AND c.idem_key = 'seed-carol-0001';

INSERT IGNORE INTO `content_like` (`user_id`, `content_id`)
SELECT u.id, c.id FROM `user` u CROSS JOIN `content` c
WHERE u.username IN ('demo_carol')
  AND c.idem_key = 'seed-dave-0001';

-- ------------------------------------------------------------
-- 回填 like_count：DB 是唯一真相，计数必须等于点赞行数
-- 只动 seed 行，不碰用户自己发的内容
-- ------------------------------------------------------------
UPDATE `content` c
SET c.like_count = (SELECT COUNT(*) FROM `content_like` l WHERE l.content_id = c.id)
WHERE c.idem_key LIKE 'seed-%';

-- 自检：应看到 8 行，like_count 合计 9
SELECT c.id, c.user_id, c.like_count, LEFT(c.body, 24) AS body
FROM `content` c
WHERE c.idem_key LIKE 'seed-%'
ORDER BY c.like_count DESC, c.id DESC;
