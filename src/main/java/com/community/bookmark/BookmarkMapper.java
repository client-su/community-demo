package com.community.bookmark;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 收藏关系表访问。
 *
 * <p>与 {@code content_like} 同构但语义不同：收藏是私有的「稍后看」，
 * 不参与 {@code like_count}、不进入热榜。唯一键 uk_user_content(user_id, content_id)
 * 保证一人一条，重复收藏由数据库拒绝。
 */
@Mapper
public interface BookmarkMapper {

    /**
     * 幂等插入：重复收藏返回 0 行受影响，而不是抛异常。
     * 与 {@code LikeMapper.insertIgnore} 同样的取舍：只适合这种幂等语义。
     */
    @Insert("""
            INSERT IGNORE INTO `bookmark` (user_id, content_id)
            VALUES (#{userId}, #{contentId})
            """)
    int insertIgnore(@Param("userId") long userId, @Param("contentId") long contentId);

    @Delete("""
            DELETE FROM `bookmark`
             WHERE user_id = #{userId}
               AND content_id = #{contentId}
            """)
    int delete(@Param("userId") long userId, @Param("contentId") long contentId);

    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM `bookmark`
                 WHERE user_id = #{userId} AND content_id = #{contentId}
            )
            """)
    boolean exists(@Param("userId") long userId, @Param("contentId") long contentId);

    /**
     * 「我收藏的」游标分页：按收藏记录 id 倒序（id 自增，越大越新），
     * 只返回未删内容。cursor 传 Long.MAX_VALUE 表示第一页。
     */
    @Select("""
            SELECT b.id AS bookmark_id,
                   c.id, c.user_id, c.body, c.like_count, c.comment_count, c.created_at,
                   u.nickname
              FROM `bookmark` b
              JOIN `content` c ON c.id = b.content_id AND c.deleted = 0
              LEFT JOIN `user` u ON u.id = c.user_id
             WHERE b.user_id = #{userId}
               AND b.id < #{cursor}
             ORDER BY b.id DESC
             LIMIT #{size}
            """)
    List<BookmarkedContentRow> pageByUser(@Param("userId") long userId,
                                          @Param("cursor") long cursor,
                                          @Param("size") int size);
}
