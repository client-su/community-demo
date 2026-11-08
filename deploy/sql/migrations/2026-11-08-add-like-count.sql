-- ============================================================
-- 2026-11-08  content 表增加 like_count
--
-- 为什么需要这一列：
--   1) 详情页/列表页展示赞数，不能每次都 COUNT(*)（content_like 会很大）
--   2) Redis 是可丢弃的加速层，必须有一个「可重建」的权威计数
--      —— 这也是第 02 步"不建 like_count"判断的修正：当时假设 Redis 不丢数据
--
-- 幂等写法：MySQL 不支持 ADD COLUMN IF NOT EXISTS，
-- 先查 information_schema 决定执行 ALTER 还是空操作。
-- ============================================================

USE `community`;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `content` ADD COLUMN `like_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''点赞数，DB 为唯一真相'' AFTER `deleted`',
        'SELECT ''like_count 已存在，跳过'''
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'content'
      AND column_name = 'like_count'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
