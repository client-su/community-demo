package com.community.comment;

import java.time.LocalDateTime;

/**
 * 评论领域对象，与 `comment` 表一一对应。
 *
 * <p>一级评论：只挂内容（content_id），不支持楼中楼。
 * `nickname` 不属于 comment 表：由查询 JOIN user 表填充（与 Content 同法）。
 */
public class Comment {

    private Long id;
    private Long contentId;
    private Long userId;
    private String nickname;
    private String body;
    private Integer deleted;
    private LocalDateTime createdAt;

    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getContentId() {
        return contentId;
    }

    public void setContentId(Long contentId) {
        this.contentId = contentId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
