-- ============================================================
-- 002  评论功能：
--   1) content 表加 comment_count（与 like_count 同款：DB 为唯一真相）
--   2) 新建 comment 表（一级评论，软删 + 游标分页）
--
-- 幂等：列用 information_schema 判断；表用 CREATE TABLE IF NOT EXISTS。
-- ============================================================

USE `community`;

-- 1) content 加 comment_count
SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `content` ADD COLUMN `comment_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''评论数，DB 为唯一真相'' AFTER `like_count`',
        'SELECT ''comment_count 已存在，跳过'''
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'content'
      AND column_name = 'comment_count'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) comment 表
CREATE TABLE IF NOT EXISTS `comment` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，同时作为游标',
    `content_id` BIGINT UNSIGNED NOT NULL                COMMENT '被评论内容 id',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '评论者 id',
    `body`       TEXT            NOT NULL                COMMENT '评论正文',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '0 正常 / 1 已删（软删）',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_content_id` (`content_id`, `id`),
    KEY `idx_user_id` (`user_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '评论表';
