package com.community.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可选鉴权：匿名也能访问，带了合法 token 仍然会识别身份。
 *
 * <p>用在只读接口上（信息流、详情、热榜）。没有它，未登录访客连首页都打不开，
 * 一个"展示用"的站点就失去了意义。
 *
 * <p>边界很清楚：
 * <ul>
 *   <li>没带 token → 放行，{@link UserContext} 保持为空；</li>
 *   <li>带了坏 token → 仍然 401。静默降级成匿名会让前端出现"已登录却没有身份"
 *       的状态，反而无法判断该不该重新登录。</li>
 * </ul>
 *
 * <p>默认方向仍是"全部接口都要登录"，这个注解是显式的例外：忘了加只是打不开，
 * 不会把写接口误开放。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalAuth {
}
