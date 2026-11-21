package com.community.tag;

import java.time.LocalDateTime;

/**
 * 标签领域对象，与 `tag` 表一一对应。
 * 第一版只有预置标签（name 唯一），没有自由标签的用户治理问题。
 */
public class Tag {

    private Long id;
    private String name;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
