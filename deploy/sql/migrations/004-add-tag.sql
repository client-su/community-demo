-- ============================================================
-- 004  标签功能：
--   1) 新建 tag 表（预置标签，name 唯一）
--   2) 新建 content_tag 关联表（uk_content_tag 幂等）
--   3) 预置标签数据（INSERT IGNORE，可重复执行）
--
-- 第一版不做自由标签：标签是系统参考数据，避免大小写/同义词治理。
-- ============================================================

USE `community`;

CREATE TABLE IF NOT EXISTS `tag` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签 id',
    `name`       VARCHAR(32)     NOT NULL                COMMENT '标签名',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '标签表';

CREATE TABLE IF NOT EXISTS `content_tag` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `content_id` BIGINT UNSIGNED NOT NULL                COMMENT '内容 id',
    `tag_id`     BIGINT UNSIGNED NOT NULL                COMMENT '标签 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_tag` (`content_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`, `content_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '内容-标签关联表';

-- 预置标签：INSERT IGNORE 靠 uk_name 保证可重复执行
INSERT IGNORE INTO `tag` (`name`) VALUES
    ('技术'), ('生活'), ('职场'), ('求助'), ('公告'), ('闲聊');
