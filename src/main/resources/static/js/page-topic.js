/**
 * 详情页：正文 + 时间 + 赞数 + 点赞/取消 + 删除（仅作者自己）。
 *
 * 后端只有「用户 #id」，没有昵称与头像，所以这里不编造，直接显示编号且不可点。
 * 匿名访问时不请求 /like（那个接口仍要求登录，会 401），只渲染详情里带的 likeCount。
 */

import { ApiError, api } from './api.js';
import { cachedMe, formatTime, handleApiError, mountShell, toast } from './shell.js';

const id = Number(new URLSearchParams(location.search).get('id'));
const root = document.querySelector('#topic');

const state = { item: null, liked: false, likeCount: 0, busy: false };
let likeBtn = null;

function stateBlock(title, sub) {
    const box = document.createElement('div');
    box.className = 'state';
    const h = document.createElement('p');
    h.className = 'state-title';
    h.textContent = title;
    const p = document.createElement('p');
    p.className = 'state-sub';
    p.textContent = sub;
    const back = document.createElement('a');
    back.className = 'btn ghost';
    back.href = '/';
    back.textContent = '回到列表';
    box.append(h, p, back);
    root.replaceChildren(box);
}

function paintLike() {
    if (!likeBtn) return;
    likeBtn.classList.toggle('liked', state.liked);
    likeBtn.textContent = `${state.liked ? '已赞' : '赞'} ${state.likeCount}`;
}

async function toggleLike() {
    if (state.busy) return;
    if (!cachedMe()) {
        const params = new URLSearchParams({ next: `/topic.html?id=${id}`, hint: '点赞需要先登录' });
        location.href = `/login.html?${params}`;
        return;
    }
    state.busy = true;
    likeBtn.disabled = true;
    try {
        // 以接口返回的 liked / likeCount 为准，不在本地 +1
        const result = state.liked ? await api.unlike(id) : await api.like(id);
        state.liked = result.liked;
        state.likeCount = result.likeCount;
        paintLike();
    } catch (error) {
        handleApiError(error, '操作失败');
    } finally {
        state.busy = false;
        likeBtn.disabled = false;
    }
}

async function removeTopic() {
    if (!window.confirm('删除后不可恢复，确定？')) return;
    try {
        await api.remove(id);
        toast('已删除');
        location.href = '/';
    } catch (error) {
        handleApiError(error, '删除失败');
    }
}

function render(item, me) {
    const head = document.createElement('header');
    head.className = 'topic-head';

    const avatar = document.createElement('span');
    avatar.className = 'avatar';
    avatar.textContent = String(item.userId);

    const meta = document.createElement('div');
    meta.className = 'topic-meta';
    const who = document.createElement('span');
    who.className = 'who';
    who.textContent = `用户 #${item.userId}`;
    const time = document.createElement('time');
    time.className = 'tiny muted';
    time.textContent = formatTime(item.createdAt);
    meta.append(who, time);
    head.append(avatar, meta);

    const body = document.createElement('p');
    body.className = 'topic-body';
    body.textContent = item.body;   // textContent：正文永远不当 HTML 解析

    const actions = document.createElement('footer');
    actions.className = 'topic-actions';

    likeBtn = document.createElement('button');
    likeBtn.type = 'button';
    likeBtn.className = 'like';
    likeBtn.addEventListener('click', toggleLike);
    paintLike();
    actions.append(likeBtn);

    if (me && me.id === item.userId) {
        const del = document.createElement('button');
        del.type = 'button';
        del.className = 'btn ghost sm';
        del.textContent = '删除';
        del.addEventListener('click', removeTopic);
        actions.append(del);
    }

    const tip = document.createElement('span');
    tip.className = 'tiny muted';
    tip.textContent = cachedMe() ? '点赞数由后端计数返回' : '登录后可以点赞';

    actions.append(tip);
    root.replaceChildren(head, body, actions);
}

async function boot() {
    const me = await mountShell({ active: 'list' });

    if (!Number.isInteger(id) || id <= 0) {
        stateBlock('链接里没有有效的内容编号', '地址应形如 /topic.html?id=12');
        return;
    }

    root.textContent = '加载中…';

    let item;
    try {
        item = await api.detail(id);
    } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
            stateBlock('内容不存在', '可能已被删除，或者链接里的编号不对。');
        } else {
            stateBlock('加载失败', error.message || '稍后刷新试试。');
        }
        return;
    }

    state.item = item;
    state.likeCount = item.likeCount ?? 0;
    document.title = `社区 · ${String(item.body || '').slice(0, 20)}`;
    render(item, me);

    // 登录态才查点赞状态：匿名调这个接口仍会 401
    if (me) {
        try {
            const result = await api.likeState(id);
            state.liked = result.liked;
            state.likeCount = result.likeCount;
            paintLike();
        } catch (error) {
            // 拿不到点赞态不影响读正文
        }
    }
}

boot();
