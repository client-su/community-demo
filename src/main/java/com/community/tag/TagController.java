package com.community.tag;

import com.community.common.OptionalAuth;
import com.community.common.Result;
import com.community.content.dto.ContentVO;
import com.community.content.dto.PageResponse;
import com.community.tag.dto.TagVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** 标签列表：匿名可读（发布页选标签用） */
    @OptionalAuth
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.ok(tagService.list());
    }

    /** 按标签读内容：匿名可读，游标分页 */
    @OptionalAuth
    @GetMapping("/{id}/contents")
    public Result<PageResponse<ContentVO>> contentsByTag(
            @PathVariable long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(tagService.contentsByTag(id, cursor, size));
    }
}
