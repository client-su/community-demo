package com.community.user;

import java.time.LocalDateTime;

/**
 * 用户领域对象，与 `user` 表一一对应。
 *
 * <p>注意：这是内部模型，不是对外响应体。
 * {@code passwordHash} 绝不允许出现在任何接口返回里——对外要用独立的 VO。
 *
 * <p>用 POJO 而非 record：MyBatis 通过 setter 赋值最稳；
 * record 需要构造器映射（依赖 -parameters 与参数名匹配），容易在打包方式变化时静默失效。
 */
public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 1 正常 / 0 禁用 */
    public static final int STATUS_ACTIVE = 1;

    public boolean isActive() {
        return status != null && status == STATUS_ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** 只用于日志，避免 passwordHash 被顺手打进日志 */
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', nickname='" + nickname
                + "', status=" + status + '}';
    }
}
