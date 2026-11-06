package com.community.common;

/**
 * 当前请求的用户上下文。
 *
 * <p>用 ThreadLocal 存 userId，业务代码不必从 Controller 一路把 userId 传到 Service。
 * 生命周期由 {@link TraceIdFilter} 管理：请求进入时为空、结束时清理。
 *
 * <p>注意：ThreadLocal 里的值不会跨线程传递。异步任务、线程池、@Async 里取不到，
 * 需要显式透传（把 userId 作为参数传进去）。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        USER_ID.set(userId);
    }

    /** 可能为 null：未登录的请求 */
    public static Long get() {
        return USER_ID.get();
    }

    /** 必须已登录的场景用这个：没有就抛 401，省掉调用方的空判断 */
    public static long require() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
