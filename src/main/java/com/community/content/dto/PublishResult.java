package com.community.content.dto;

/**
 * 发布结果：created=false 表示这是一次幂等重放，返回的是之前那条内容。
 */
public record PublishResult(ContentVO content, boolean created) {
}
