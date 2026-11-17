package com.community.like;

import java.time.LocalDateTime;

/**
 * 「我赞过的内容」查询结果行：likeId 是游标（content_like.id），
 * 其余字段用来拼装 ContentVO。用 POJO 而非 record——MyBatis 靠 setter 赋值最稳。
 */
public class LikedContentRow {

    private Long likeId;
    private Long id;
    private Long userId;
    private String nickname;
    private String body;
    private Integer likeCount;
    private LocalDateTime createdAt;

    public Long getLikeId() {
        return likeId;
    }

    public void setLikeId(Long likeId) {
        this.likeId = likeId;
    }

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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
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
}
