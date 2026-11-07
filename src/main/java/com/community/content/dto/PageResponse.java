package com.community.content.dto;

import java.util.List;

/**
 * 游标分页结果。
 *
 * <p>没有 total：大表 COUNT(*) 很贵，且"总数"在无限流场景本就没有意义。
 * hasMore 用"多取一条"判断，成本是一次索引扫描的 1 条额外记录。
 *
 * @param items      本页数据
 * @param nextCursor 下一页游标；hasMore=false 时为 null
 * @param hasMore    是否还有下一页
 */
public record PageResponse<T>(List<T> items, Long nextCursor, boolean hasMore) {
}
