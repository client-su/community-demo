package com.community.content;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ContentMapper {

    @Insert("""
            INSERT INTO `content` (user_id, idem_key, body, deleted)
            VALUES (#{userId}, #{idemKey}, #{body}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Content content);

    /** 详情：软删的内容视为不存在 */
    @Select("""
            SELECT id, user_id, idem_key, body, deleted, like_count, created_at, updated_at
              FROM `content`
             WHERE id = #{id}
               AND deleted = 0
            """)
    Optional<Content> findVisibleById(@Param("id") Long id);

    /**
     * 游标分页。
     *
     * <p>等值列 deleted 在前、范围列 id 在后，与 idx_deleted_id(deleted, id) 的列顺序一致，
     * 因此可以做到「定位到 deleted=0 段 + 反向扫描」。
     * cursor 传 Long.MAX_VALUE 表示第一页——用参数表达分支，避免动态 SQL。
     */
    @Select("""
            SELECT id, user_id, body, like_count, created_at, updated_at
              FROM `content`
             WHERE deleted = 0
               AND id < #{cursor}
             ORDER BY id DESC
             LIMIT #{size}
            """)
    List<Content> page(@Param("cursor") long cursor, @Param("size") int size);

    /**
     * 软删：存在性 + 归属 + 未删 三重校验一次性完成。
     *
     * <p>不写“先查再判”是因为那会引入 TOCTOU 竞态窗口；
     * 返回 0 行即代表“不存在 / 不是你的 / 已删”三者之一，调用方统一当 404。
     */
    @Update("""
            UPDATE `content`
               SET deleted = 1
             WHERE id = #{id}
               AND user_id = #{userId}
               AND deleted = 0
            """)
    int softDeleteOwnedBy(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, user_id, body, like_count, created_at, updated_at
              FROM `content`
             WHERE deleted = 0
               AND id IN
               <foreach item="id" collection="ids" open="(" separator="," close=")">
                 #{id}
               </foreach>
            </script>
            """)
    List<Content> findVisibleByIds(@Param("ids") List<Long> ids);

    /**
     * 计数加减，永不小于 0。
     *
     * <p>like_count 是 INT UNSIGNED：直接 like_count - 1 在 0 时会报
     * "BIGINT UNSIGNED value is out of range"（严格模式），所以先 CAST 成有符号再算，
     * 最后用 GREATEST 夹到 0。
     *
     * @param delta +1 点赞 / -1 取消
     */
    @Update("""
            UPDATE `content`
               SET like_count = GREATEST(CAST(like_count AS SIGNED) + #{delta}, 0)
             WHERE id = #{id}
               AND deleted = 0
            """)
    int addLikeCount(@Param("id") long id, @Param("delta") int delta);

    /** 热榜重建用：按赞数取 Top N（走 idx_deleted_id 过滤 + 文件排序，低频调用可接受） */
    @Select("""
            SELECT id, user_id, body, like_count, created_at, updated_at
              FROM `content`
             WHERE deleted = 0
               AND like_count > 0
             ORDER BY like_count DESC, id DESC
             LIMIT #{limit}
            """)
    List<Content> topByLikeCount(@Param("limit") int limit);
}
