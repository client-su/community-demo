-- ============================================================
-- 用户中心 + 内容社区 · 建表脚本
-- 执行：mysql -uroot < deploy/sql/schema.sql
-- 可重复执行：全部使用 IF NOT EXISTS
-- ============================================================

CREATE DATABASE IF NOT EXISTS `community`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `community`;

-- ------------------------------------------------------------
-- 用户表
--   uk_username  登录名唯一：注册时的并发查重由数据库兜底
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(32)     NOT NULL                COMMENT '登录名，全局唯一',
    `password_hash` VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 哈希（60 字符），字段名即约定：不存明文',
    `nickname`      VARCHAR(32)     NOT NULL DEFAULT ''     COMMENT '昵称',
    `status`        TINYINT         NOT NULL DEFAULT 1      COMMENT '1 正常 / 0 禁用',
    `role`          TINYINT         NOT NULL DEFAULT 0      COMMENT '0 普通 / 1 管理员',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';

-- ------------------------------------------------------------
-- 内容表
--   uk_user_idem   (user_id, idem_key) 幂等：同一次发布的重试只落一条
--   idx_deleted_id (deleted, id)       信息流：软删过滤 + id 倒序游标分页
--   idx_user_id    (user_id)           作者维度：查某人发过什么
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `content` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，同时作为游标',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '作者 id',
    `idem_key`   VARCHAR(64)     NOT NULL                COMMENT '幂等键，客户端生成，重试时复用',
    `body`       TEXT            NOT NULL                COMMENT '正文',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '0 正常 / 1 已删（软删）',
    `like_count` INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '点赞数，DB 为唯一真相',
    `comment_count` INT UNSIGNED NOT NULL DEFAULT 0      COMMENT '评论数，DB 为唯一真相',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_idem` (`user_id`, `idem_key`),
    KEY `idx_deleted_id` (`deleted`, `id`),
    KEY `idx_user_id` (`user_id`),
    FULLTEXT KEY `ft_body` (`body`) WITH PARSER ngram
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '内容表';

-- ------------------------------------------------------------
-- 点赞关系表
--   uk_user_content (user_id, content_id) 一人一条：重复点赞由数据库拒绝
--   idx_content     (content_id)          反向查询：「这条内容有多少赞 / 谁赞了」
--   注意：唯一键最左前缀是 user_id，只按 content_id 查用不上它，故单建 idx_content
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `content_like` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '点赞用户 id',
    `content_id` BIGINT UNSIGNED NOT NULL                COMMENT '被赞内容 id',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_content` (`user_id`, `content_id`),
    KEY `idx_content` (`content_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '点赞关系表';

-- ------------------------------------------------------------
-- 收藏关系表
--   与 content_like 同构但语义不同：收藏是私有的「稍后看」，
--   不参与 like_count、不进入热榜。
--   uk_user_content (user_id, content_id) 一人一条：重复收藏由数据库拒绝
--   idx_content     (content_id)          反向查询
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 评论表（一级评论：只挂内容，不支持楼中楼）
--   idx_content_id (content_id, id) 某内容的评论列表：游标分页
--   idx_user_id    (user_id, id)    某用户发出的评论（预留）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 标签表（预置标签，name 唯一；第一版不做自由标签）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tag` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签 id',
    `name`       VARCHAR(32)     NOT NULL                COMMENT '标签名',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '标签表';

-- ------------------------------------------------------------
-- 内容-标签关联表
--   uk_content_tag (content_id, tag_id) 同一内容同一标签只绑一次
--   idx_tag_id     (tag_id, content_id) 按标签查内容（游标分页）
-- ------------------------------------------------------------
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

-- 预置标签：INSERT IGNORE 靠 uk_name 保证可重复执行（系统参考数据，不是演示数据）
INSERT IGNORE INTO `tag` (`name`) VALUES
    ('技术'), ('生活'), ('职场'), ('求助'), ('公告'), ('闲聊');
