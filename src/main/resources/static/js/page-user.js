/**
 * 用户主页：资料卡 + 该用户发布的内容（游标分页）。
 * 匿名可看，与详情页一样用 @OptionalAuth。
 */

import { api } from './api.js';
import { formatDate, formatTime, handleApiError, initial, mountShell } from './shell.js';

const id = Number(new URLSearchParams(location.search).get('id'));
const PAGE_SIZE = 10;

const el = {
    profile: document.querySelector('#profile'),
    rows: document.querySelector('#rows'),
    empty: document.querySelector('#empty'),
    more: document.querySelector('#moreBtn'),
};

const state = { cursor: null, hasMore: false, loading: false };

function truncate(text, max) {
    const flat = String(text || '').replace(/\s+/g, ' ').trim();
    return flat.length > max ? `${flat.slice(0, max)}…` : flat;
}

function rowEl(item) {
    const li = document.createElement('li');
    li.className = 'row';

    const link = document.createElement('a');
    link.className = 'row-title';
    link.href = `/topic.html?id=${item.id}`;
    link.textContent = truncate(item.body, 70) || `内容 #${item.id}`;

    const meta = document.createElement('p');
    meta.className = 'row-meta';
    meta.textContent = `${formatTime(item.createdAt)} · ${item.likeCount ?? 0} 赞`;

    const main = document.createElement('div');
    main.className = 'row-main';
    main.append(link, meta);
    li.append(main);
    return li;
}

function paintProfile(user) {
    el.profile.replaceChildren();

    const head = document.createElement('div');
    head.className = 'me-head';
    const avatar = document.createElement('span');
    avatar.className = 'avatar lg';
    avatar.textContent = initial(user);
    const info = document.createElement('div');
    const name = document.createElement('h1');
    name.className = 'page-title';
    name.textContent = user.nickname || user.username;
    const username = document.createElement('p');
    username.className = 'muted tiny';
    username.textContent = `@${user.username}`;
    info.append(name, username);
    head.append(avatar, info);

    const kv = document.createElement('dl');
    kv.className = 'kv';
    const row = document.createElement('div');
    const dt = document.createElement('dt');
    dt.textContent = '注册时间';
    const dd = document.createElement('dd');
    dd.textContent = formatDate(user.createdAt) || '—';
    row.append(dt, dd);
    kv.append(row);

    el.profile.append(head, kv);
}

async function loadList(reset) {
    if (state.loading) return;
    state.loading = true;
    el.more.disabled = true;
    try {
        const page = await api.userContents(id, reset ? null : state.cursor, PAGE_SIZE);
        state.cursor = page.nextCursor;
        state.hasMore = page.hasMore;
        if (reset) el.rows.replaceChildren();
        page.items.forEach((item) => el.rows.append(rowEl(item)));
        el.more.hidden = !page.hasMore;
        el.empty.hidden = el.rows.children.length > 0;
    } catch (error) {
        handleApiError(error, '加载失败');
        el.empty.hidden = el.rows.children.length > 0;
    } finally {
        state.loading = false;
        el.more.disabled = false;
    }
}

async function boot() {
    await mountShell({ active: 'list' });

    if (!Number.isInteger(id) || id <= 0) {
        el.profile.replaceChildren();
        el.profile.textContent = '链接里没有有效的用户编号';
        return;
    }

    let user;
    try {
        user = await api.user(id);
    } catch (error) {
        handleApiError(error, '加载失败');
        el.profile.textContent = '用户不存在';
        return;
    }

    document.title = `社区 · ${user.nickname || user.username}`;
    paintProfile(user);
    await loadList(true);
    el.more.addEventListener('click', () => loadList(false));
}

boot();
