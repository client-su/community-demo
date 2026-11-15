/**
 * 页面逻辑：只用 DOM API + 事件，没有任何框架。
 *
 * 三条约定（换成 Vue/React 时能少返工）：
 * 1. 不写裸 fetch，所有请求走 api.js；
 * 2. 用户内容一律用 textContent 写入，不拼 HTML，天然免疫 XSS；
 * 3. 点赞数、点赞态一律以接口返回为准，不在本地自增。
 */

import { ApiError, api, getToken, onRequest, setToken } from './api.js';

const PAGE_SIZE = 10;

const el = {
    authArea: document.querySelector('#authArea'),
    composer: document.querySelector('#composer'),
    composerBody: document.querySelector('#composerBody'),
    composerHint: document.querySelector('#composerHint'),
    bodyCount: document.querySelector('#bodyCount'),
    publishBtn: document.querySelector('#publishBtn'),
    feed: document.querySelector('#feed'),
    feedEmpty: document.querySelector('#feedEmpty'),
    loadMoreBtn: document.querySelector('#loadMoreBtn'),
    refreshBtn: document.querySelector('#refreshBtn'),
    hot: document.querySelector('#hot'),
    reqPanel: document.querySelector('#reqPanel'),
    reqLog: document.querySelector('#reqLog'),
    reqToggle: document.querySelector('#reqToggle'),
    toast: document.querySelector('#toast'),
    authDialog: document.querySelector('#authDialog'),
    authTitle: document.querySelector('#authTitle'),
    authForm: document.querySelector('#authForm'),
    authMsg: document.querySelector('#authMsg'),
    authSubmit: document.querySelector('#authSubmit'),
    nicknameRow: document.querySelector('#nicknameRow'),
    switchMode: document.querySelector('#switchMode'),
    detailDialog: document.querySelector('#detailDialog'),
    detailBody: document.querySelector('#detailBody'),
};

const state = {
    me: null,
    cursor: null,
    hasMore: false,
    loadingFeed: false,
    likedIds: new Set(),
    authMode: 'login',
};

/* ---------------- 小工具 ---------------- */

function newKey() {
    return crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function formatTime(value) {
    if (!value) return '';
    const date = new Date(String(value).replace(' ', 'T'));
    if (Number.isNaN(date.getTime())) return String(value);
    const pad = (n) => String(n).padStart(2, '0');
    const hm = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    const sameDay = date.toDateString() === new Date().toDateString();
    return sameDay ? hm : `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${hm}`;
}

const truncate = (text, max) => (text.length > max ? `${text.slice(0, max)}…` : text);

let toastTimer = 0;

function toast(message, kind = 'ok') {
    el.toast.textContent = message;
    el.toast.className = `toast ${kind}`;
    el.toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        el.toast.hidden = true;
    }, 2600);
}

function handleApiError(error, fallback) {
    if (error instanceof ApiError && error.status === 429) {
        const wait = error.retryAfter ? `，${error.retryAfter} 秒后再试` : '，请稍后再试';
        toast(`请求过于频繁${wait}`, 'error');
        return;
    }
    toast(error.message || fallback, 'error');
}

/* ---------------- 请求日志面板 ---------------- */

onRequest((entry) => {
    const li = document.createElement('li');
    li.className = entry.failed || !entry.ok ? 'bad' : 'ok';

    const time = document.createElement('span');
    time.className = 't';
    time.textContent = new Date().toLocaleTimeString('zh-CN', { hour12: false });

    const call = document.createElement('span');
    call.className = 'm';
    call.textContent = `${entry.method} ${entry.path}`;

    const status = document.createElement('span');
    status.className = 's';
    status.textContent = entry.status === 0 ? 'ERR' : String(entry.status);

    const detail = document.createElement('span');
    detail.className = 'd';
    const parts = [`${entry.ms}ms`];
    if (entry.code !== undefined && entry.code !== 0) parts.unshift(`code=${entry.code}`);
    if (entry.traceId) parts.push(entry.traceId.slice(0, 8));
    detail.textContent = parts.join(' · ');

    li.append(time, call, status, detail);
    el.reqLog.prepend(li);
    while (el.reqLog.children.length > 40) {
        el.reqLog.lastElementChild.remove();
    }
});

el.reqToggle.addEventListener('click', () => {
    const collapsed = el.reqPanel.classList.toggle('collapsed');
    el.reqToggle.textContent = collapsed ? '展开' : '收起';
});

/* ---------------- 登录态 ---------------- */

function renderAuthArea() {
    el.authArea.replaceChildren();
    if (state.me) {
        const who = document.createElement('span');
        who.className = 'who';
        who.textContent = state.me.nickname || state.me.username;

        const out = document.createElement('button');
        out.type = 'button';
        out.className = 'ghost';
        out.textContent = '退出';
        out.addEventListener('click', signOut);

        el.authArea.append(who, out);
        return;
    }

    const login = document.createElement('button');
    login.type = 'button';
    login.textContent = '登录';
    login.addEventListener('click', () => openAuth('login'));

    const register = document.createElement('button');
    register.type = 'button';
    register.className = 'ghost';
    register.textContent = '注册';
    register.addEventListener('click', () => openAuth('register'));

    el.authArea.append(login, register);
}

function renderComposer() {
    const loggedIn = Boolean(state.me);
    el.publishBtn.disabled = !loggedIn;
    el.composerHint.replaceChildren();

    if (loggedIn) {
        el.composerHint.textContent = '同一份草稿重复发布只会创建一条（幂等键）';
        return;
    }
    const link = document.createElement('button');
    link.type = 'button';
    link.className = 'link';
    link.textContent = '登录后可以发布';
    link.addEventListener('click', () => openAuth('login'));
    el.composerHint.append(link);
}

function applyAuthMode() {
    const registering = state.authMode === 'register';
    el.authTitle.textContent = registering ? '注册' : '登录';
    el.authSubmit.textContent = registering ? '注册并登录' : '登录';
    el.switchMode.textContent = registering ? '已有账号，去登录' : '没有账号，去注册';
    el.nicknameRow.hidden = !registering;
}

function openAuth(mode = 'login', message = '') {
    state.authMode = mode;
    el.authForm.reset();
    applyAuthMode();
    el.authMsg.textContent = message;
    if (!el.authDialog.open) el.authDialog.showModal();
}

el.switchMode.addEventListener('click', () => {
    state.authMode = state.authMode === 'login' ? 'register' : 'login';
    el.authMsg.textContent = '';
    applyAuthMode();
    el.authTitle.focus();
});

async function signIn(username, password) {
    const result = await api.login({ username, password });
    setToken(result.token);           // 只存 token，用户信息用 /api/me 单独查
    state.me = await api.me();
    state.likedIds.clear();
    renderAuthArea();
    renderComposer();
    await loadFeed(true);             // 登录后重拉：删除按钮等依赖"我是谁"
}

el.authForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const data = new FormData(el.authForm);
    const username = String(data.get('username') || '').trim();
    const password = String(data.get('password') || '');
    const nickname = String(data.get('nickname') || '').trim();

    el.authSubmit.disabled = true;
    el.authMsg.textContent = '';
    try {
        if (state.authMode === 'register') {
            const payload = nickname ? { username, password, nickname } : { username, password };
            await api.register(payload);
            await signIn(username, password);   // 注册后直接登录，少一步操作
            toast('注册成功，已为你登录');
        } else {
            await signIn(username, password);
            toast('登录成功');
        }
        el.authDialog.close();
    } catch (error) {
        el.authMsg.textContent = error.message;
    } finally {
        el.authSubmit.disabled = false;
    }
});

async function signOut() {
    try {
        await api.logout();           // 把 jti 写进黑名单，票立刻作废
    } catch (error) {
        // 票本来就过期了也要清本地，否则页面会卡在"以为还登录着"
    }
    setToken('');
    state.me = null;
    state.likedIds.clear();
    renderAuthArea();
    renderComposer();
    await loadFeed(true);
    toast('已退出登录');
}

window.addEventListener('auth:expired', () => {
    state.me = null;
    state.likedIds.clear();
    renderAuthArea();
    renderComposer();
    toast('登录已过期，请重新登录', 'error');
});

/* ---------------- 信息流 ---------------- */

function paintLike(button, liked, count) {
    button.classList.toggle('liked', liked);
    button.textContent = `${liked ? '已赞' : '赞'} ${count}`;
}

function postEl(item) {
    const li = document.createElement('li');
    li.className = 'post';
    li.dataset.id = item.id;

    const head = document.createElement('div');
    head.className = 'post-head';
    const who = document.createElement('span');
    who.className = 'who';
    who.textContent = `用户 #${item.userId}`;
    const time = document.createElement('time');
    time.className = 'muted tiny';
    time.textContent = formatTime(item.createdAt);
    head.append(who, time);

    const body = document.createElement('p');
    body.className = 'post-body';
    body.textContent = item.body;   // textContent：用户内容永远不当 HTML 解析

    const actions = document.createElement('div');
    actions.className = 'post-actions';

    const likeBtn = document.createElement('button');
    likeBtn.type = 'button';
    likeBtn.className = 'like';
    const count = item.likeCount ?? 0;
    paintLike(likeBtn, state.likedIds.has(item.id), count);
    likeBtn.addEventListener('click', () => toggleLike(item.id, likeBtn));
    actions.append(likeBtn);

    if (state.me && state.me.id === item.userId) {
        const del = document.createElement('button');
        del.type = 'button';
        del.className = 'ghost tiny';
        del.textContent = '删除';
        del.addEventListener('click', () => removePost(item.id));
        actions.append(del);
    }

    li.append(head, body, actions);
    return li;
}

async function toggleLike(id, button) {
    if (!state.me) {
        openAuth('login', '点赞需要先登录');
        return;
    }
    const liked = state.likedIds.has(id);
    button.disabled = true;
    try {
        const result = liked ? await api.unlike(id) : await api.like(id);
        // 以接口返回的 liked/likeCount 为准：本地 +1 在重复点击时一定和真相脱节
        if (result.liked) {
            state.likedIds.add(id);
        } else {
            state.likedIds.delete(id);
        }
        paintLike(button, result.liked, result.likeCount);
        loadHot();
    } catch (error) {
        handleApiError(error, '操作失败');
    } finally {
        button.disabled = false;
    }
}

async function removePost(id) {
    if (!window.confirm('删除后不可恢复，确定？')) return;
    try {
        await api.remove(id);
        el.feed.querySelector(`[data-id="${id}"]`)?.remove();
        el.feedEmpty.hidden = el.feed.children.length > 0;
        toast('已删除');
        loadHot();
    } catch (error) {
        handleApiError(error, '删除失败');
    }
}

function prependOrUpdate(item) {
    const existing = el.feed.querySelector(`[data-id="${item.id}"]`);
    if (existing) {
        existing.replaceWith(postEl(item));   // 幂等重放返回同 id，覆盖而不是再插一条
    } else {
        el.feed.prepend(postEl(item));
    }
    el.feedEmpty.hidden = true;
}

async function loadFeed(reset = false) {
    if (state.loadingFeed) return;
    state.loadingFeed = true;
    try {
        const page = await api.feed(reset ? null : state.cursor, PAGE_SIZE);
        state.cursor = page.nextCursor;
        state.hasMore = page.hasMore;
        if (reset) el.feed.replaceChildren();
        page.items.forEach((item) => el.feed.append(postEl(item)));
        el.feedEmpty.hidden = el.feed.children.length > 0;
        el.loadMoreBtn.hidden = !page.hasMore;
    } catch (error) {
        handleApiError(error, '加载信息流失败');
    } finally {
        state.loadingFeed = false;
    }
}

el.loadMoreBtn.addEventListener('click', () => loadFeed(false));
el.refreshBtn.addEventListener('click', () => {
    el.feed.replaceChildren();
    loadFeed(true);
    loadHot();
});

/* ---------------- 发布 ---------------- */

/**
 * 幂等键跟着"这一份草稿"走，不跟着"这一次点击"走。
 * 内容没改就复用同一把钥匙：网络抖动重试、手抖双击都只会创建一条。
 */
let draft = { key: '', body: '' };

function keyForDraft(body) {
    if (!draft.key || draft.body !== body) {
        draft = { key: newKey(), body };
    }
    return draft.key;
}

el.composerBody.addEventListener('input', () => {
    el.bodyCount.textContent = String(el.composerBody.value.length);
    el.publishBtn.disabled = !state.me || !el.composerBody.value.trim();
});

el.composerBody.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
        event.preventDefault();
        el.composer.requestSubmit();
    }
});

el.composer.addEventListener('submit', async (event) => {
    event.preventDefault();
    const body = el.composerBody.value.trim();
    if (!body || !state.me) return;

    el.publishBtn.disabled = true;
    try {
        const created = await api.publish(body, keyForDraft(body));
        prependOrUpdate(created);
        el.composerBody.value = '';
        el.bodyCount.textContent = '0';
        draft = { key: '', body: '' };
        toast('已发布');
        loadHot();
    } catch (error) {
        // 失败保留 draft.key：用户直接重试时仍是同一个幂等键
        handleApiError(error, '发布失败');
    } finally {
        el.publishBtn.disabled = !state.me || !el.composerBody.value.trim();
    }
});

/* ---------------- 热榜与详情 ---------------- */

async function loadHot() {
    try {
        const items = await api.hot(10);
        if (!items.length) {
            el.hot.innerHTML = '<li class="muted tiny">还没有点赞数据</li>';
            return;
        }
        el.hot.replaceChildren(...items.map((item, index) => {
            const li = document.createElement('li');
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'hot-item';

            const rank = document.createElement('span');
            rank.className = 'rank';
            rank.textContent = String(index + 1);

            const text = document.createElement('span');
            text.className = 'hot-text';
            text.textContent = truncate(item.body, 22);

            const count = document.createElement('span');
            count.className = 'hot-count';
            count.textContent = `${item.likeCount ?? 0} 赞`;

            button.append(rank, text, count);
            button.addEventListener('click', () => showDetail(item.id));
            li.append(button);
            return li;
        }));
    } catch (error) {
        el.hot.innerHTML = '<li class="muted tiny">热榜加载失败</li>';
    }
}

async function showDetail(id) {
    try {
        const item = await api.detail(id);
        el.detailBody.replaceChildren(postEl(item));
        el.detailDialog.showModal();
    } catch (error) {
        handleApiError(error, '加载详情失败');
    }
}

/* ---------------- 启动 ---------------- */

async function boot() {
    renderAuthArea();
    renderComposer();

    if (getToken()) {
        try {
            state.me = await api.me();
        } catch (error) {
            state.me = null;         // 票过期了就是匿名状态，不该挡住页面
        }
        renderAuthArea();
        renderComposer();
    }

    await Promise.all([loadFeed(true), loadHot()]);
}

boot();
