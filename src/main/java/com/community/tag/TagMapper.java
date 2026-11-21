package com.community.tag;

import com.community.content.Content;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 标签与内容-标签关联访问。
 *
 * <p>uk_content_tag(content_id, tag_id) 保证同一内容同一标签只绑一次；
 * 关联写入用 INSERT IGNORE，与幂等发布配合不会产生重复行。
 */
@Mapper
public interface TagMapper {

    @Select("""
            SELECT id, name, created_at
              FROM `tag`
             ORDER BY id
            """)
    List<Tag> findAll();

    @Select("""
            SELECT id, name, created_at
              FROM `tag`
             WHERE id = #{id}
            """)
    Optional<Tag> findById(@Param("id") long id);

    /**
     * 按标签读内容（游标分页）：走 idx_tag_id(tag_id, content_id) 定位该标签的段，
     * 再按内容 id 反向扫描。只返回未删内容。
     */
    @Select("""
            SELECT c.id, c.user_id, c.body, c.like_count, c.comment_count, c.created_at, c.updated_at,
                   u.nickname
              FROM `content_tag` ct
              JOIN `content` c ON c.id = ct.content_id AND c.deleted = 0
              LEFT JOIN `user` u ON u.id = c.user_id
             WHERE ct.tag_id = #{tagId}
               AND c.id < #{cursor}
             ORDER BY c.id DESC
             LIMIT #{size}
            """)
    List<Content> pageContentsByTag(@Param("tagId") long tagId,
                                    @Param("cursor") long cursor,
                                    @Param("size") int size);

    /** 批量确认哪些 id 真实存在：发布绑定前校验，返回的是已存在的 id 集合 */
    @Select("""
            <script>
            SELECT id FROM `tag`
             WHERE id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">
              #{id}
            </foreach>
            </script>
            """)
    List<Long> findExistingIds(@Param("ids") List<Long> ids);

    /** 幂等批量绑定：重复绑定由 uk_content_tag 兜底 */
    @Insert("""
            <script>
            INSERT IGNORE INTO `content_tag` (content_id, tag_id)
            VALUES
            <foreach item="tagId" collection="tagIds" separator=",">
              (#{contentId}, #{tagId})
            </foreach>
            </script>
            """)
    int insertContentTags(@Param("contentId") long contentId,
                          @Param("tagIds") List<Long> tagIds);
}
