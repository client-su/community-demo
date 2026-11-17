package com.community.content;

import java.time.LocalDateTime;

/**
 * 内容领域对象。
 *
 * <p>`deleted` 是软删标记：内容被删后仍要保留，因为点赞记录、审计、统计都可能引用它。
 * 代价是每条查询都必须带 deleted = 0，漏一处就是 bug。
 *
 * <p>`nickname` 不属于 content 表：由查询 JOIN `user` 表填充，用于直接组装对外 VO，
 * 避免 Service 层为每条内容再发一次查用户。
 */
public class Content {

    private Long id;
    private Long userId;
    private String nickname;
    private String idemKey;
    private String body;
    private Integer deleted;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public void setIdemKey(String idemKey) {
        this.idemKey = idemKey;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
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
}
