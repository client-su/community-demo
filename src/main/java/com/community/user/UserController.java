package com.community.user;

import com.community.auth.JwtAuthInterceptor;
import com.community.auth.JwtPayload;
import com.community.bookmark.BookmarkService;
import com.community.common.OptionalAuth;
import com.community.common.Result;
import com.community.content.ContentService;
import com.community.content.dto.ContentVO;
import com.community.content.dto.PageResponse;
import com.community.like.LikeService;
import com.community.user.dto.ChangePasswordRequest;
import com.community.user.dto.ProfileVO;
import com.community.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final ContentService contentService;
    private final LikeService likeService;
    private final BookmarkService bookmarkService;

    public UserController(UserService userService, ContentService contentService,
                          LikeService likeService, BookmarkService bookmarkService) {
        this.userService = userService;
        this.contentService = contentService;
        this.likeService = likeService;
        this.bookmarkService = bookmarkService;
    }

    /** 注意：没有 userId 参数——身份只从 token 来 */
    @GetMapping("/me")
    public Result<ProfileVO> me() {
        return Result.ok(userService.currentProfile());
    }

    /** 部分更新：只改传了的字段，同样没有 userId 参数 */
    @PatchMapping("/me")
    public Result<ProfileVO> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateCurrentProfile(request));
    }

    /** 修改密码：成功后当前 token 立即失效，前端按 401 引导重新登录 */
    @PatchMapping("/me/password")
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestAttribute(JwtAuthInterceptor.PAYLOAD_ATTRIBUTE) JwtPayload payload) {
        userService.changePassword(request, payload);
        return Result.ok();
    }

    /** 公开资料：匿名可看（用户主页用） */
    @OptionalAuth
    @GetMapping("/users/{id}")
    public Result<ProfileVO> publicProfile(@PathVariable long id) {
        return Result.ok(userService.publicProfile(id));
    }

    /** 某人发布的内容：匿名可看 */
    @OptionalAuth
    @GetMapping("/users/{id}/contents")
    public Result<PageResponse<ContentVO>> userContents(
            @PathVariable long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.userContents(id, cursor, size));
    }

    /** 我发布的内容：需要登录 */
    @GetMapping("/me/contents")
    public Result<PageResponse<ContentVO>> myContents(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.myContents(cursor, size));
    }

    /** 我赞过的内容：需要登录 */
    @GetMapping("/me/likes")
    public Result<PageResponse<ContentVO>> myLikes(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(likeService.likedByMe(cursor, size));
    }

    /** 我收藏的内容：需要登录，游标按收藏先后倒序 */
    @GetMapping("/me/bookmarks")
    public Result<PageResponse<ContentVO>> myBookmarks(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(bookmarkService.bookmarkedByMe(cursor, size));
    }
}
