package com.community.content;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
            SELECT id, user_id, idem_key, body, deleted, created_at, updated_at
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
            SELECT id, user_id, body, created_at, updated_at
              FROM `content`
             WHERE deleted = 0
               AND id < #{cursor}
             ORDER BY id DESC
             LIMIT #{size}
            """)
    List<Content> page(@Param("cursor") long cursor, @Param("size") int size);
}
