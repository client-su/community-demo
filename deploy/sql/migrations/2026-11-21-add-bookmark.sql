-- ============================================================
-- 2026-11-21  收藏功能：新增 bookmark 表
--
-- 与 content_like 同构但语义不同：收藏是私有的「稍后看」，
-- 不参与 like_count、不进入热榜。唯一键保证一人一条。
--
-- 幂等：CREATE TABLE IF NOT EXISTS 可重复执行。
-- ============================================================

USE `community`;

CREATE TABLE IF NOT EXISTS `bookmark` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，同时作为游标',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '收藏者 id',
    `content_id` BIGINT UNSIGNED NOT NULL                COMMENT '被收藏内容 id',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_content` (`user_id`, `content_id`),
    KEY `idx_content` (`content_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '收藏关系表';
