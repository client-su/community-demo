package com.community.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级限流声明。放在 Controller 方法上，由 {@link RateLimitInterceptor} 执行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 动作名，参与 Redis key：rate:{action}:{subject}:{bucket} */
    String action();

    /** 窗口内允许的次数 */
    int limit() default 10;

    /** 窗口秒数 */
    int windowSeconds() default 60;

    Subject by() default Subject.USER;

    enum Subject {
        /** 按调用方 IP：登录等未认证接口用 */
        IP,
        /** 按当前登录用户：业务接口更准确，NAT 后的多个用户互不影响 */
        USER
    }
}
