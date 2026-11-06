package com.community.user;

import com.community.common.Result;
import com.community.user.dto.ProfileVO;
import com.community.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
}
