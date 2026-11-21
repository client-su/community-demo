package com.community.comment;

import com.community.comment.dto.CommentVO;
import com.community.comment.dto.CreateCommentRequest;
import com.community.common.OptionalAuth;
import com.community.common.Result;
import com.community.content.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** 评论列表：匿名可读，游标分页 */
    @OptionalAuth
    @GetMapping("/api/contents/{contentId}/comments")
    public Result<PageResponse<CommentVO>> list(
            @PathVariable long contentId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(commentService.list(contentId, cursor, size));
    }

    /** 发评论：创建成功返回 201 */
    @PostMapping("/api/contents/{contentId}/comments")
    public ResponseEntity<Result<CommentVO>> create(
            @PathVariable long contentId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(commentService.create(contentId, request)));
    }

    /** 删评论：只能删自己的，失败统一 40402 */
    @DeleteMapping("/api/comments/{id}")
    public Result<Void> delete(@PathVariable long id) {
        commentService.delete(id);
        return Result.ok();
    }
}
