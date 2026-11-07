package com.community.content;

import com.community.common.Result;
import com.community.content.dto.ContentVO;
import com.community.content.dto.CreateContentRequest;
import com.community.content.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @PostMapping
    public ResponseEntity<Result<ContentVO>> publish(@Valid @RequestBody CreateContentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(contentService.publish(request)));
    }

    /** 游标分页：不传 cursor 即第一页；下一页用上一页返回的 nextCursor */
    @GetMapping
    public Result<PageResponse<ContentVO>> feed(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.feed(cursor, size));
    }

    @GetMapping("/{id}")
    public Result<ContentVO> detail(@PathVariable long id) {
        return Result.ok(contentService.detail(id));
    }

    /** 逻辑删除；只能删自己的，失败统一 404 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        contentService.delete(id);
        return Result.ok();
    }
}
