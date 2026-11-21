package com.community.bookmark;

import java.time.LocalDateTime;

/**
 * 「我收藏的」查询结果行：bookmarkId 是游标（bookmark.id），
 * 其余字段用来拼装 ContentVO。用 POJO 而非 record——MyBatis 靠 setter 赋值最稳。
 */
public class BookmarkedContentRow {

    private Long bookmarkId;
    private Long id;
    private Long userId;
    private String nickname;
    private String body;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createdAt;

    public Long getBookmarkId() {
        return bookmarkId;
    }

    public void setBookmarkId(Long bookmarkId) {
        this.bookmarkId = bookmarkId;
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

    public Integer getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
