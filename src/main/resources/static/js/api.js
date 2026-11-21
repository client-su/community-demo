/**
 * 接口层：所有请求都从这里出。
 *
 * 为什么单独一个文件：将来若换成 Vue/React，只替换渲染层，这个文件可以原样带走。
 * 也正因为如此，页面里不允许出现裸 fetch。
 */

const TOKEN_KEY = 'community.token';

/** 统一错误：把 HTTP 状态码和业务码一起带出来，页面才能分别处理 */
export class ApiError extends Error {
    constructor({ status, code, message, retryAfter, traceId }) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
        this.traceId = traceId;
    }
}

let token = localStorage.getItem(TOKEN_KEY) || '';

export function getToken() {
    return token;
}

export function setToken(value) {
    token = value || '';
    if (token) {
        localStorage.setItem(TOKEN_KEY, token);
    } else {
        localStorage.removeItem(TOKEN_KEY);
    }
}

/* ---------- 请求日志：给右下角悬浮窗用 ---------- */

const listeners = new Set();

/** 悬浮窗要跨页面看，所以除了内存广播，还把每条记录写进 sessionStorage */
const REQLOG_KEY = 'community.reqlog';
// 存储上限 50 条；面板只渲染最近 10 条（由 reqpanel 控制），窗口不会随请求数长高
const REQLOG_MAX = 50;

export function readRequestLog() {
    try {
        const raw = sessionStorage.getItem(REQLOG_KEY);
        const list = raw ? JSON.parse(raw) : [];
        return Array.isArray(list) ? list : [];
    } catch (error) {
        return [];
    }
}

export function clearRequestLog() {
    sessionStorage.removeItem(REQLOG_KEY);
}

function persist(entry) {
    try {
        const list = readRequestLog();
        list.push({ ...entry, ts: Date.now() });
        // 只留最近 50 条：日志是给人看的，不是埋点
        while (list.length > REQLOG_MAX) list.shift();
        sessionStorage.setItem(REQLOG_KEY, JSON.stringify(list));
    } catch (error) {
        // sessionStorage 被禁用/写满时不值得拖垮主流程
    }
}

export function onRequest(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

function emit(entry) {
    persist(entry);
    listeners.forEach((fn) => fn(entry));
}

/* ---------- 核心请求 ---------- */

async function request(method, path, { body, auth = false, idempotencyKey, withMeta = false } = {}) {
    const headers = {};
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    if (idempotencyKey) {
        headers['Idempotency-Key'] = idempotencyKey;
    }
    // 只读接口故意不带 token：票过期时匿名读照样能看，不会因为一张旧票把首页打成 401
    if (auth && token) {
        headers.Authorization = `Bearer ${token}`;
    }

    const started = performance.now();
    let response;
    let payload = null;

    try {
        response = await fetch(path, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
        });
        payload = await response.json().catch(() => null);
    } catch (error) {
        emit({ method, path, status: 0, ms: Math.round(performance.now() - started), failed: true });
        throw new ApiError({ status: 0, code: -1, message: '网络异常，或服务没有启动' });
    }

    // traceId 由后端 TraceIdFilter 回写到响应头，日志和响应能对上
    const traceId = response.headers.get('X-Trace-Id');
    const code = payload && typeof payload.code === 'number' ? payload.code : undefined;
    const message = (payload && payload.message) || `HTTP ${response.status}`;

    emit({
        method,
        path,
        status: response.status,
        code,
        message,
        traceId,
        ms: Math.round(performance.now() - started),
        ok: response.ok,
    });

    // 票坏了就地清掉：否则下一次请求还会带着这张废票
    if (response.status === 401 && auth) {
        setToken('');
        window.dispatchEvent(new CustomEvent('auth:expired'));
    }

    if (!response.ok || code !== 0) {
        throw new ApiError({
            status: response.status,
            code: code ?? response.status,
            message,
            retryAfter: response.headers.get('Retry-After'),
            traceId,
        });
    }

    // withMeta：演示页需要区分 201（首次创建）与 200（幂等重放）
    return withMeta
        ? { data: payload.data, status: response.status, traceId }
        : payload.data;
}

/** 拼查询串：null/undefined 不参与，避免出现 ?cursor=null */
function query(params) {
    const pairs = Object.entries(params)
        .filter(([, value]) => value !== null && value !== undefined && value !== '')
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`);
    return pairs.length ? `?${pairs.join('&')}` : '';
}

/* ---------- 业务接口 ---------- */

export const api = {
    register: (payload) => request('POST', '/api/auth/register', { body: payload }),
    login: (payload) => request('POST', '/api/auth/login', { body: payload }),
    logout: () => request('POST', '/api/auth/logout', { auth: true }),
    me: () => request('GET', '/api/me', { auth: true }),
    updateProfile: (payload) => request('PATCH', '/api/me', { body: payload, auth: true }),

    feed: (cursor, size = 10) => request('GET', `/api/contents${query({ cursor, size })}`),
    search: (q, cursor, size = 10) =>
        request('GET', `/api/contents/search${query({ q, cursor, size })}`),
    detail: (id) => request('GET', `/api/contents/${id}`),
    publish: (body, idempotencyKey, tagIds = null) =>
        request('POST', '/api/contents', { body: { body, tagIds }, auth: true, idempotencyKey }),
    publishMeta: (body, idempotencyKey) =>
        request('POST', '/api/contents', { body: { body }, auth: true, idempotencyKey, withMeta: true }),
    remove: (id) => request('DELETE', `/api/contents/${id}`, { auth: true }),

    like: (id) => request('POST', `/api/contents/${id}/like`, { auth: true }),
    unlike: (id) => request('DELETE', `/api/contents/${id}/like`, { auth: true }),
    likeState: (id) => request('GET', `/api/contents/${id}/like`, { auth: true }),

    bookmark: (id) => request('POST', `/api/contents/${id}/bookmark`, { auth: true }),
    unbookmark: (id) => request('DELETE', `/api/contents/${id}/bookmark`, { auth: true }),
    bookmarkState: (id) => request('GET', `/api/contents/${id}/bookmark`, { auth: true }),
    myBookmarks: (cursor, size = 10) =>
        request('GET', `/api/me/bookmarks${query({ cursor, size })}`, { auth: true }),

    comments: (id, cursor, size = 10) =>
        request('GET', `/api/contents/${id}/comments${query({ cursor, size })}`),
    comment: (id, body) =>
        request('POST', `/api/contents/${id}/comments`, { body: { body }, auth: true }),
    removeComment: (id) => request('DELETE', `/api/comments/${id}`, { auth: true }),

    tags: () => request('GET', '/api/tags'),
    tagContents: (id, cursor, size = 10) =>
        request('GET', `/api/tags/${id}/contents${query({ cursor, size })}`),

    changePassword: (payload) => request('PATCH', '/api/me/password', { body: payload, auth: true }),

    hot: (limit = 10) => request('GET', `/api/contents/hot${query({ limit })}`),

    // 用户主页与「我的」维度（/api/users/* 匿名可看，/api/me/* 需要登录）
    user: (id) => request('GET', `/api/users/${id}`),
    userContents: (id, cursor, size = 10) =>
        request('GET', `/api/users/${id}/contents${query({ cursor, size })}`),
    myContents: (cursor, size = 10) =>
        request('GET', `/api/me/contents${query({ cursor, size })}`, { auth: true }),
    myLikes: (cursor, size = 10) =>
        request('GET', `/api/me/likes${query({ cursor, size })}`, { auth: true }),
};
