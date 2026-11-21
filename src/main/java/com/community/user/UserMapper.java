package com.community.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * 用户表访问。
 *
 * <p>注解 SQL 而不是 XML：语句少时改动成本最低，不会出现"改了接口忘了 XML"。
 * 出现动态 SQL（如按条件拼接）时再迁到 XML。
 */
@Mapper
public interface UserMapper {

    /** 列名显式列出，避免以后加列把整个表结构变化带进查询 */
    @Select("""
            SELECT id, username, password_hash, nickname, status, role, created_at, updated_at
              FROM `user`
             WHERE username = #{username}
            """)
    Optional<User> findByUsername(@Param("username") String username);

    @Select("""
            SELECT id, username, password_hash, nickname, status, role, created_at, updated_at
              FROM `user`
             WHERE id = #{id}
            """)
    Optional<User> findById(@Param("id") Long id);

    /**
     * 插入用户；主键由数据库生成后回填到 {@code user.id}。
     *
     * <p>用户名重复时数据库抛 1062，MyBatis 包装为 DuplicateKeyException，
     * 由 GlobalExceptionHandler 转成 409——这里不做"先查再插"。
     */
    @Insert("""
            INSERT INTO `user` (username, password_hash, nickname, status)
            VALUES (#{username}, #{passwordHash}, #{nickname}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(User user);

    /** updated_at 由数据库的 ON UPDATE CURRENT_TIMESTAMP 自动维护 */
    @Update("UPDATE `user` SET nickname = #{nickname} WHERE id = #{id}")
    int updateNickname(@Param("id") Long id, @Param("nickname") String nickname);

    /** 修改密码：只更新哈希列，明文不落库也不经手 */
    @Update("UPDATE `user` SET password_hash = #{passwordHash} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /** status: 1 正常 / 0 禁用（封禁/解封） */
    @Update("UPDATE `user` SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") int status);

    /** 鉴权拦截器用：只取 status，不拉 password_hash（封禁校验的最小查询） */
    @Select("SELECT status FROM `user` WHERE id = #{id}")
    Optional<Integer> findStatusById(@Param("id") long id);
}
