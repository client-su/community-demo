-- ============================================================
-- 2026-11-21  账号安全：user 表增加 role 列
--
-- role: 0 普通 / 1 管理员。封禁/解封复用已有 status 列（1 正常 / 0 禁用）。
--
-- 幂等写法：先查 information_schema 决定执行 ALTER 还是空操作。
-- ============================================================

USE `community`;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `user` ADD COLUMN `role` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 普通 / 1 管理员'' AFTER `status`',
        'SELECT ''role 已存在，跳过'''
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND column_name = 'role'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
