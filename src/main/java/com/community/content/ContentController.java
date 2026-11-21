package com.community.content;

import com.community.common.OptionalAuth;
import com.community.common.Result;
import com.community.content.dto.ContentVO;
import com.community.content.dto.CreateContentRequest;
import com.community.content.dto.PageResponse;
import com.community.content.dto.PublishResult;
import com.community.like.LikeService;
import com.community.like.dto.LikeResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;
    private final LikeService likeService;

    public ContentController(ContentService contentService, LikeService likeService) {
        this.contentService = contentService;
        this.likeService = likeService;
    }

    @PostMapping
    public ResponseEntity<Result<ContentVO>> publish(
            @Valid @RequestBody CreateContentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        PublishResult result = contentService.publish(request, idemKey);
        // 首次创建 201，幂等重放 200（客户端据此判断是不是新写的）
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Result.ok(result.content()));
    }

    /** 游标分页：不传 cursor 即第一页；下一页用上一页返回的 nextCursor */
    @OptionalAuth
    @GetMapping
    public Result<PageResponse<ContentVO>> feed(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.feed(cursor, size));
    }

    /**
     * 关键词搜索：结果同样游标分页。
     * 声明在 /{id} 之前只是为阅读清晰：Spring 会优先字面量路径，二者并不冲突。
     */
    @OptionalAuth
    @GetMapping("/search")
    public Result<PageResponse<ContentVO>> search(
            @RequestParam String q,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.search(q, cursor, size));
    }

    @OptionalAuth
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

    @PostMapping("/{id}/like")
    public Result<LikeResult> like(@PathVariable long id) {
        return Result.ok(likeService.like(id));
    }

    @DeleteMapping("/{id}/like")
    public Result<LikeResult> unlike(@PathVariable long id) {
        return Result.ok(likeService.unlike(id));
    }

    /** 查询点赞状态：前端用 liked 渲染按钮，用 likeCount 渲染数字 */
    @GetMapping("/{id}/like")
    public Result<LikeResult> likeState(@PathVariable long id) {
        return Result.ok(likeService.state(id));
    }

    /** 热榜：Top N 内容 */
    @OptionalAuth
    @GetMapping("/hot")
    public Result<List<ContentVO>> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(contentService.hot(limit));
    }
}
