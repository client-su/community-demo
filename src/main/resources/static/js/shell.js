/**
 * 公共外壳：顶栏、页脚、登录态缓存、toast、错误提示。
 *
 * 多页形态下每个页面都要重新判登录态，所以这里集中做两件事：
 * 1. 先用 sessionStorage 里的 /api/me 结果同步渲染一次（避免刷新时按钮闪成"未登录"）；
 * 2. 再后台请求一次 /api/me 校验，票过期了就清缓存并重渲染。
 */

import { ApiError, api, getToken, setToken } from './api.js';
import { mountRequestPanel } from './reqpanel.js';

const ME_KEY = 'community.me';

let me = null;

/* ---------------- 登录态 ---------------- */

export function cachedMe() {
    if (me) return me;
    try {
        const raw = sessionStorage.getItem(ME_KEY);
        me = raw ? JSON.parse(raw) : null;
    } catch (error) {
        me = null;
    }
    return me;
}

export async function loadMe({ force = false } = {}) {
    if (!getToken()) {
        me = null;
        sessionStorage.removeItem(ME_KEY);
        return null;
    }
    if (!force && me) return me;
    try {
        me = await api.me();
        sessionStorage.setItem(ME_KEY, JSON.stringify(me));
    } catch (error) {
        // 票过期/被拉黑：当作匿名，不要卡住页面
        me = null;
        sessionStorage.removeItem(ME_KEY);
    }
    return me;
}

export function clearMe() {
    me = null;
    sessionStorage.removeItem(ME_KEY);
}

/** 改完昵称后把最新的 ProfileVO 写回缓存，顶栏与资料页立刻一致 */
export function cacheMe(user) {
    me = user;
    try {
        sessionStorage.setItem(ME_KEY, JSON.stringify(user));
    } catch (error) {
        // 缓存失败不影响本次会话内的显示
    }
}

export async function signOut() {
    try {
        await api.logout();       // 把 jti 写进黑名单，票立刻作废
    } catch (error) {
        // 票本来就失效了也要清本地，否则页面会以为还登录着
    }
    setToken('');
    clearMe();
    location.href = '/';
}

export function displayName(user) {
    return (user && (user.nickname || user.username)) || '未登录';
}

/** 内容条目的作者显示名：昵称优先，没有就退回「用户 #id」（ContentVO 可能不带昵称） */
export function authorName(item) {
    return (item && item.nickname) ? item.nickname : `用户 #${item && item.userId}`;
}

export function initial(user) {
    const name = displayName(user).trim();
    return name ? name.charAt(0).toUpperCase() : '?';
}

/* ---------------- 时间 ---------------- */

function toDate(value) {
    if (!value) return null;
    // 后端返回 "2026-11-15T19:51:24"（没有时区），直接交给 Date 即可
    const date = new Date(String(value).replace(' ', 'T'));
    return Number.isNaN(date.getTime()) ? null : date;
}

const pad = (n) => String(n).padStart(2, '0');

/** 今天只显示 HH:mm，其余显示 MM-DD HH:mm */
export function formatTime(value) {
    const date = toDate(value);
    if (!date) return '';
    const hm = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    const sameDay = date.toDateString() === new Date().toDateString();
    return sameDay ? hm : `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${hm}`;
}

/** YYYY-MM-DD */
export function formatDate(value) {
    const date = toDate(value);
    if (!date) return '';
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/* ---------------- 跳转地址 ---------------- */

export function loginUrl(next = location.pathname + location.search, mode = 'login') {
    const params = new URLSearchParams({ next });
    if (mode === 'register') params.set('mode', 'register');
    return `/login.html?${params}`;
}

/** 没登录就把人送去登录页，登录完回到当前页 */
export function requireLogin(next = location.pathname + location.search, hint = '') {
    if (cachedMe()) return true;
    const params = new URLSearchParams({ next });
    if (hint) params.set('hint', hint);
    location.href = `/login.html?${params}`;
    return false;
}

/* ---------------- 提示条 ---------------- */

let toastTimer = 0;

export function toast(message, kind = 'ok') {
    let el = document.querySelector('.toast');
    if (!el) {
        el = document.createElement('div');
        el.className = 'toast';
        document.body.append(el);
    }
    el.textContent = message;
    el.className = `toast ${kind}`;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        el.hidden = true;
    }, 2800);
}

export function handleApiError(error, fallback) {
    if (error instanceof ApiError && error.status === 429) {
        const wait = error.retryAfter ? `，${error.retryAfter} 秒后再试` : '，请稍后再试';
        toast(`请求过于频繁${wait}`, 'error');
        return;
    }
    toast((error && error.message) || fallback, 'error');
}

/* ---------------- 外壳 ---------------- */

const NAV = [
    { key: 'list', href: '/', text: '最新' },
    { key: 'hot', href: '/?tab=hot', text: '热榜' },
    { key: 'submit', href: '/submit.html', text: '写内容' },
    { key: 'docs', href: '/docs.html', text: '接口' },
    { key: 'lab', href: '/lab.html', text: '演示' },
];

function buildHeader(active) {
    const header = document.createElement('header');
    header.className = 'topbar';

    const inner = document.createElement('div');
    inner.className = 'topbar-inner';

    const brand = document.createElement('a');
    brand.className = 'brand';
    brand.href = '/';
    brand.append(document.createTextNode('社区'), Object.assign(document.createElement('span'), { textContent: '.' }));

    const nav = document.createElement('nav');
    nav.className = 'nav';
    NAV.forEach((item) => {
        const link = document.createElement('a');
        link.href = item.href;
        link.textContent = item.text;
        if (item.key === active) link.classList.add('active');
        nav.append(link);
    });

    const authArea = document.createElement('div');
    authArea.className = 'auth-area';
    authArea.id = 'authArea';

    inner.append(brand, nav, authArea);
    header.append(inner);
    return header;
}


function renderAuthArea() {
    const area = document.querySelector('#authArea');
    if (!area) return;
    area.replaceChildren();
    const user = cachedMe();

    if (!user) {
        // 已经在登录页了，再放一个「登录」按钮指向自己就是绕圈
        if (location.pathname.endsWith('/login.html')) return;

        const login = document.createElement('a');
        login.className = 'btn primary';
        login.href = loginUrl();
        login.textContent = '登录';

        const register = document.createElement('a');
        register.className = 'btn ghost';
        register.href = loginUrl(location.pathname + location.search, 'register');
        register.textContent = '注册';

        area.append(login, register);
        return;
    }

    const profile = document.createElement('a');
    profile.className = 'me-link';
    profile.href = '/me.html';
    const avatar = document.createElement('span');
    avatar.className = 'avatar sm';
    avatar.textContent = initial(user);
    const name = document.createElement('span');
    name.className = 'who';
    name.textContent = displayName(user);
    profile.append(avatar, name);

    const out = document.createElement('button');
    out.type = 'button';
    out.className = 'btn ghost';
    out.textContent = '退出';
    out.addEventListener('click', signOut);

    area.append(profile, out);
}

/** 页内改了昵称等资料后，让顶栏跟着变 */
export function refreshAuthArea() {
    renderAuthArea();
}

/**
 * 挂载外壳。返回当前的 me（可能为 null）。
 * 先用缓存渲染，再后台校验，避免刷新时顶栏闪一下"登录"。
 */
export async function mountShell({ active = '' } = {}) {
    document.body.prepend(buildHeader(active));
    renderAuthArea();
    mountRequestPanel();

    window.addEventListener('auth:expired', () => {
        clearMe();
        renderAuthArea();
        toast('登录已过期，请重新登录', 'error');
    });

    if (getToken()) {
        await loadMe({ force: true });
        renderAuthArea();
    }
    return cachedMe();
}
