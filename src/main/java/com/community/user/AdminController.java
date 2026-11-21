package com.community.user;

import com.community.common.Result;
import com.community.user.dto.SetUserStatusRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端接口：封禁/解封用户。
 *
 * <p>管理员身份校验在 Service 层（requireAdmin 查 role），
 * 非管理员访问统一 403。目标用户不能是自己——防止把最后的管理员锁在门外。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    /** 封禁（status=0）/ 解封（status=1） */
    @PutMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable long id,
                                  @Valid @RequestBody SetUserStatusRequest request) {
        userService.setUserStatus(id, request.status());
        return Result.ok();
    }
}
