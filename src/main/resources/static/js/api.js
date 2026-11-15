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

/* ---------- 请求日志：给页面右下角的"请求日志"面板用 ---------- */

const listeners = new Set();

export function onRequest(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

function emit(entry) {
    listeners.forEach((fn) => fn(entry));
}

/* ---------- 核心请求 ---------- */

async function request(method, path, { body, auth = false, idempotencyKey } = {}) {
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

    return payload.data;
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

    feed: (cursor, size = 10) => request('GET', `/api/contents${query({ cursor, size })}`),
    detail: (id) => request('GET', `/api/contents/${id}`),
    publish: (body, idempotencyKey) =>
        request('POST', '/api/contents', { body: { body }, auth: true, idempotencyKey }),
    remove: (id) => request('DELETE', `/api/contents/${id}`, { auth: true }),

    like: (id) => request('POST', `/api/contents/${id}/like`, { auth: true }),
    unlike: (id) => request('DELETE', `/api/contents/${id}/like`, { auth: true }),
    likeState: (id) => request('GET', `/api/contents/${id}/like`, { auth: true }),

    hot: (limit = 10) => request('GET', `/api/contents/hot${query({ limit })}`),
};
