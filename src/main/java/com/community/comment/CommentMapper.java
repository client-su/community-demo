package com.community.comment;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CommentMapper {

    @Insert("""
            INSERT INTO `comment` (content_id, user_id, body, deleted)
            VALUES (#{contentId}, #{userId}, #{body}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Comment comment);

    /**
     * 某条内容下的评论（游标分页）。
     *
     * <p>idx_content_id(content_id, id) 与 WHERE 的等值列在前、范围列在后一致，
     * 定位到该内容的段后按 id 反向扫描。cursor 传 Long.MAX_VALUE 表示第一页。
     */
    @Select("""
            SELECT cm.id, cm.content_id, cm.user_id, cm.body, cm.deleted, cm.created_at,
                   u.nickname
              FROM `comment` cm
              LEFT JOIN `user` u ON u.id = cm.user_id
             WHERE cm.content_id = #{contentId}
               AND cm.deleted = 0
               AND cm.id < #{cursor}
             ORDER BY cm.id DESC
             LIMIT #{size}
            """)
    List<Comment> pageByContent(@Param("contentId") long contentId,
                                @Param("cursor") long cursor,
                                @Param("size") int size);

    /** 软删的评论视为不存在 */
    @Select("""
            SELECT cm.id, cm.content_id, cm.user_id, cm.body, cm.deleted, cm.created_at,
                   u.nickname
              FROM `comment` cm
              LEFT JOIN `user` u ON u.id = cm.user_id
             WHERE cm.id = #{id}
               AND cm.deleted = 0
            """)
    Optional<Comment> findVisibleById(@Param("id") long id);

    /** 软删：归属校验由 Service 完成（先查后删，拿到 content_id 用于回减计数） */
    @Update("""
            UPDATE `comment`
               SET deleted = 1
             WHERE id = #{id}
               AND deleted = 0
            """)
    int softDelete(@Param("id") long id);
}
