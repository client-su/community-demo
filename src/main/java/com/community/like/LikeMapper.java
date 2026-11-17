package com.community.like;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 点赞关系表访问。
 *
 * <p>唯一键 uk_user_content(user_id, content_id) 是"一人一条"的最后防线：
 * 即使 Redis 里的去重集合丢了，数据库也不会出现重复关系。
 */
@Mapper
public interface LikeMapper {

    /**
     * 幂等插入：重复点赞时返回 0 行受影响，而不是抛异常。
     *
     * <p>注意 INSERT IGNORE 会把所有错误降级为 warning（列超长、非空约束等也会被吞），
     * 所以它只适合这种"幂等插入"语义，不能当通用兜底。
     */
    @Insert("""
            INSERT IGNORE INTO `content_like` (user_id, content_id)
            VALUES (#{userId}, #{contentId})
            """)
    int insertIgnore(@Param("userId") long userId, @Param("contentId") long contentId);

    @Delete("""
            DELETE FROM `content_like`
             WHERE user_id = #{userId}
               AND content_id = #{contentId}
            """)
    int delete(@Param("userId") long userId, @Param("contentId") long contentId);

    /** 用唯一键做覆盖索引计数，不回表 */
    @Select("SELECT COUNT(*) FROM `content_like` WHERE content_id = #{contentId}")
    long countByContent(@Param("contentId") long contentId);

    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM `content_like`
                 WHERE user_id = #{userId} AND content_id = #{contentId}
            )
            """)
    boolean exists(@Param("userId") long userId, @Param("contentId") long contentId);

    /** 重建 Redis 集合用：把某内容的全部点赞者捞出来 */
    @Select("SELECT user_id FROM `content_like` WHERE content_id = #{contentId}")
    java.util.List<Long> findUserIdsByContent(@Param("contentId") long contentId);

    /**
     * 「我赞过的内容」游标分页：按点赞记录 id 倒序（id 自增，越大越新），
     * 只返回未删内容。cursor 传 Long.MAX_VALUE 表示第一页。
     */
    @Select("""
            SELECT l.id AS like_id,
                   c.id, c.user_id, c.body, c.like_count, c.created_at,
                   u.nickname
              FROM `content_like` l
              JOIN `content` c ON c.id = l.content_id AND c.deleted = 0
              LEFT JOIN `user` u ON u.id = c.user_id
             WHERE l.user_id = #{userId}
               AND l.id < #{cursor}
             ORDER BY l.id DESC
             LIMIT #{size}
            """)
    java.util.List<LikedContentRow> pageLikedByUser(@Param("userId") long userId,
                                                    @Param("cursor") long cursor,
                                                    @Param("size") int size);
}
