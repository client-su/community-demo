/**
 * 「我的」页：看资料 + 改昵称 + 退出，加上「我发布的 / 我赞过的」两个 tab。
 */

import { api } from './api.js';
import { cacheMe, formatDate, formatTime, handleApiError, initial, mountShell, refreshAuthArea, signOut, toast } from './shell.js';

const PAGE_SIZE = 10;

const el = {
    avatar: document.querySelector('#avatar'),
    name: document.querySelector('#name'),
    username: document.querySelector('#username'),
    createdAt: document.querySelector('#createdAt'),
    userId: document.querySelector('#userId'),
    form: document.querySelector('#nickForm'),
    nickname: document.querySelector('#nickname'),
    save: document.querySelector('#save'),
    signout: document.querySelector('#signout'),
    rows: document.querySelector('#rows'),
    empty: document.querySelector('#empty'),
    emptyTitle: document.querySelector('#emptyTitle'),
    emptySub: document.querySelector('#emptySub'),
    more: document.querySelector('#moreBtn'),
};

let tab = 'contents';
const state = { cursor: null, hasMore: false, loading: false };

function truncate(text, max) {
    const flat = String(text || '').replace(/\s+/g, ' ').trim();
    return flat.length > max ? `${flat.slice(0, max)}…` : flat;
}

function paint(me) {
    el.avatar.textContent = initial(me);
    el.name.textContent = me.nickname || me.username;
    el.username.textContent = `@${me.username}`;
    el.createdAt.textContent = formatDate(me.createdAt) || '—';
    el.userId.textContent = String(me.id);
    el.nickname.value = me.nickname || '';
}

async function save(event) {
    event.preventDefault();
    const nickname = el.nickname.value.trim();
    if (!nickname) {
        toast('昵称不能为空白', 'error');
        return;
    }
    el.save.disabled = true;
    try {
        const updated = await api.updateProfile({ nickname });
        cacheMe(updated);
        paint(updated);
        refreshAuthArea();
        toast('已保存');
    } catch (error) {
        handleApiError(error, '保存失败');
    } finally {
        el.save.disabled = false;
    }
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

function setEmpty(show, title, sub) {
    el.empty.hidden = !show;
    if (title) el.emptyTitle.textContent = title;
    if (sub) el.emptySub.textContent = sub;
}

async function loadList(reset) {
    if (state.loading) return;
    state.loading = true;
    el.more.disabled = true;
    try {
        const page = tab === 'contents'
            ? await api.myContents(reset ? null : state.cursor, PAGE_SIZE)
            : tab === 'likes'
                ? await api.myLikes(reset ? null : state.cursor, PAGE_SIZE)
                : await api.myBookmarks(reset ? null : state.cursor, PAGE_SIZE);
        state.cursor = page.nextCursor;
        state.hasMore = page.hasMore;
        if (reset) el.rows.replaceChildren();
        page.items.forEach((item) => el.rows.append(rowEl(item)));
        el.more.hidden = !page.hasMore;
        const emptyTitle = tab === 'contents' ? '还没有发布内容'
            : tab === 'likes' ? '还没有赞过内容' : '还没有收藏内容';
        setEmpty(el.rows.children.length === 0, emptyTitle, '');
    } catch (error) {
        handleApiError(error, '加载失败');
        setEmpty(el.rows.children.length === 0);
    } finally {
        state.loading = false;
        el.more.disabled = false;
    }
}

function switchTab(next) {
    if (tab === next) return;
    tab = next;
    document.querySelectorAll('#meTabs a').forEach((a) => {
        a.classList.toggle('active', a.dataset.tab === tab);
    });
    state.cursor = null;
    loadList(true);
}

async function boot() {
    const me = await mountShell({ active: 'me' });
    if (!me) {
        const params = new URLSearchParams({ next: '/me.html', hint: '请先登录' });
        location.href = `/login.html?${params}`;
        return;
    }
    paint(me);
    el.form.addEventListener('submit', save);
    el.signout.addEventListener('click', signOut);

    document.querySelectorAll('#meTabs a').forEach((a) => {
        a.addEventListener('click', (event) => {
            event.preventDefault();
            switchTab(a.dataset.tab);
        });
    });
    el.more.addEventListener('click', () => loadList(false));
    loadList(true);
}

boot();

