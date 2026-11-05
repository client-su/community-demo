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
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';
