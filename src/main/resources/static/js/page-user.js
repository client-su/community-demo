/**
 * 用户主页：资料卡 + 该用户发布的内容（游标分页）。
 * 匿名可看，与详情页一样用 @OptionalAuth。
 */

import { api } from './api.js';
import { formatDate, handleApiError, initial, mountShell } from './shell.js';
import { createCursorPager } from './paging.js';
import { contentRow } from './ui/content-row.js';

const id = Number(new URLSearchParams(location.search).get('id'));
const PAGE_SIZE = 10;

const el = {
    profile: document.querySelector('#profile'),
    rows: document.querySelector('#rows'),
    empty: document.querySelector('#empty'),
    more: document.querySelector('#moreBtn'),
};

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

    const idRow = document.createElement('div');
    const idDt = document.createElement('dt');
    idDt.textContent = '用户 ID';
    const idDd = document.createElement('dd');
    idDd.textContent = String(user.id);
    idRow.append(idDt, idDd);

    kv.append(row, idRow);

    el.profile.append(head, kv);
}

const pager = createCursorPager({
    container: el.rows,
    more: el.more,
    size: PAGE_SIZE,
    fetchPage: (cursor, size) => api.userContents(id, cursor, size),
    renderItem: (item) => contentRow(item),
    onEmpty: () => {
        el.empty.hidden = el.rows.children.length > 0;
    },
    onError: handleApiError,
    errorText: '加载失败',
});

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
    await pager.reset();
    el.more.addEventListener('click', () => pager.load());
}

boot();
