-- ============================================================
-- 003  搜索功能：content.body 加 FULLTEXT(ngram) 索引
--
-- MySQL 8 的 ngram 是内置中文分词器（默认 2 字切分），
-- utf8mb4 下直接可用，不需要额外插件。
--
-- 幂等写法：MySQL 的 ALTER TABLE 没有 IF NOT EXISTS，
-- 先查 information_schema.statistics 决定执行 ALTER 还是空操作。
-- ============================================================

USE `community`;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `content` ADD FULLTEXT INDEX `ft_body` (`body`) WITH PARSER ngram',
        'SELECT ''ft_body 已存在，跳过'''
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'content'
      AND index_name = 'ft_body'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
