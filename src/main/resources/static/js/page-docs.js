/**
 * 接口总览页：把 API.md / ARCHITECTURE.md 的要点做成静态可读页面。
 * 纯静态数据，不发任何请求——面试时照着讲。
 */

import { mountShell } from './shell.js';

const ENDPOINTS = [
    ['POST', '/api/auth/register', '匿名', '注册，创建成功返回 201'],
    ['POST', '/api/auth/login', '匿名', '登录，按 IP 限流 5 次 / 分钟'],
    ['POST', '/api/auth/logout', '需要', '登出，jti 进黑名单'],
    ['GET', '/api/me', '需要', '当前用户资料'],
    ['PATCH', '/api/me', '需要', '部分更新（只改传了的字段）'],
    ['GET', '/api/contents', '匿名', '游标分页信息流'],
    ['POST', '/api/contents', '需要', '发布，支持 Idempotency-Key'],
    ['GET', '/api/contents/{id}', '匿名', '详情（带缓存）'],
    ['DELETE', '/api/contents/{id}', '需要', '软删，只能删自己的'],
    ['POST', '/api/contents/{id}/like', '需要', '点赞，幂等'],
    ['DELETE', '/api/contents/{id}/like', '需要', '取消点赞'],
    ['GET', '/api/contents/{id}/like', '需要', '当前用户点赞态'],
    ['GET', '/api/contents/hot', '匿名', '热榜 Top N'],
    ['GET', '/api/users/{id}', '匿名', '公开资料'],
    ['GET', '/api/users/{id}/contents', '匿名', '某人发布的内容'],
    ['GET', '/api/me/contents', '需要', '我发布的'],
    ['GET', '/api/me/likes', '需要', '我赞过的'],
];

const ERROR_CODES = [
    ['0', '200', '成功'],
    ['40000', '400', '参数校验失败'],
    ['40100', '401', '未登录 / 过期 / 缺 Bearer'],
    ['40101', '401', '用户名或密码错误'],
    ['40300', '403', '没有操作权限'],
    ['40400', '404', '资源不存在'],
    ['40401', '404', '内容不存在或已删除'],
    ['40500', '405', '方法不支持'],
    ['40900', '409', '资源冲突'],
    ['40901', '409', '用户名已被占用'],
    ['42900', '429', '请求过于频繁'],
    ['50000', '500', '服务器内部错误'],
];

const REDIS_ROLES = [
    ['注销黑名单', 'jwt:blacklist:{jti}', 'String', 'token 剩余有效期'],
    ['详情缓存', 'content:detail:{id}', 'String(JSON)', '10 分钟'],
    ['点赞去重', 'like:content:{id}', 'Set', '7 天'],
    ['热榜', 'hot:content', 'ZSet', '不设 / 懒重建'],
    ['限流', 'rate:{action}:{ip}:{bucket}', 'String 计数', '窗口长度'],
    ['幂等占位', 'idem:content:{userId}:{key}', 'String', '处理中 30s / 完成 24h'],
];

const DECISIONS = [
    ['统一响应体 + 5 位错误码', '每个接口格式各异', 'Result{code,message,data} + 错误码 = HTTP(3位)+序号(2位)'],
    ['JWT + jti 黑名单登出', '无状态 token 无法主动作废', '登出把 jti 写 Redis，TTL = 剩余有效期'],
    ['游标分页而非 offset', '深翻页重复/漏条', 'WHERE id < cursor ORDER BY id DESC'],
    ['详情缓存空值 __NULL__', '不存在 id 反复打库', '查不到也缓存短 TTL 空标记'],
    ['点赞幂等靠唯一键', '重复点赞加爆计数', 'INSERT IGNORE，inserted==1 才加计数'],
    ['GREATEST(x-1, 0)', '裸减 1 在 0 上报 1690', 'GREATEST(CAST(like_count AS SIGNED)+delta, 0)'],
    ['热榜 ZSet 懒重建', '实时排序开销大', 'ZINCRBY 维护，丢失时读取路径重建'],
    ['固定窗口限流', '登录可被爆破', 'INCR + 首请求 EXPIRE'],
    ['幂等键 + DB 唯一键', '网络重试重复发布', 'Redis 记响应 + uk_user_idem 兜底'],
    ['Redis 故障降级', 'Redis 抖动用户点不了赞', '写路径以 DB 为准，Redis 失败只 log.warn'],
];

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function section(title, note) {
    const wrap = el('section', 'docs-section');
    const head = el('header', 'docs-head');
    head.append(el('h2', null, title));
    if (note) head.append(el('span', 'tiny muted', note));
    wrap.append(head);
    return wrap;
}

function renderEndpoints() {
    const sec = section('接口', '17 个 · 鉴权列「匿名」= 无需登录，但带坏 token 仍 401');
    const list = el('div', 'ep-list');
    ENDPOINTS.forEach(([method, path, auth, note]) => {
        const row = el('div', 'ep-row');
        row.append(el('span', `method ${method.toLowerCase()}`, method));
        row.append(el('code', 'ep-path', path));
        row.append(el('span', `ep-auth ${auth === '需要' ? 'need' : 'open'}`, auth));
        row.append(el('span', 'ep-note', note));
        list.append(row);
    });
    sec.append(list);
    return sec;
}

function renderErrorCodes() {
    const sec = section('错误码', '5 位 = HTTP 状态码(3位) + 同族序号(2位)');
    const list = el('div', 'ep-list');
    ERROR_CODES.forEach(([code, http, meaning]) => {
        const row = el('div', 'ep-row');
        row.append(el('code', 'ep-code', code));
        row.append(el('span', 'ep-http', http));
        row.append(el('span', 'ep-note', meaning));
        list.append(row);
    });
    sec.append(list);
    return sec;
}

function renderRedisRoles() {
    const sec = section('Redis 的 6 个角色', '一句话：DB 是真相，Redis 是加速与去重');
    const list = el('div', 'ep-list');
    REDIS_ROLES.forEach(([role, key, type, ttl]) => {
        const row = el('div', 'ep-row');
        row.append(el('span', 'ep-role', role));
        row.append(el('code', 'ep-path', key));
        row.append(el('span', 'ep-http', type));
        row.append(el('span', 'ep-note', ttl));
        list.append(row);
    });
    sec.append(list);
    return sec;
}

function renderDecisions() {
    const sec = section('10 条设计决策', '每条都写「问题 → 方案」，代价见 ARCHITECTURE.md');
    const list = el('ol', 'decision-list');
    DECISIONS.forEach(([name, problem, solution]) => {
        const li = el('li', null);
        li.append(el('strong', null, name));
        li.append(el('span', 'decision-problem', `问题：${problem}`));
        li.append(el('span', 'decision-solution', `方案：${solution}`));
        list.append(li);
    });
    sec.append(list);
    return sec;
}

async function boot() {
    await mountShell({ active: 'docs' });
    const root = document.querySelector('#docs');
    root.append(renderEndpoints(), renderErrorCodes(), renderRedisRoles(), renderDecisions());
}

boot();
