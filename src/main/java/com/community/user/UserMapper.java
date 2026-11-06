package com.community.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
            SELECT id, username, password_hash, nickname, status, created_at, updated_at
              FROM `user`
             WHERE username = #{username}
            """)
    Optional<User> findByUsername(@Param("username") String username);

    @Select("""
            SELECT id, username, password_hash, nickname, status, created_at, updated_at
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
}
