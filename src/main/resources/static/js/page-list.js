/**
 * 列表页：最新（游标分页）/ 热榜（Top 20）两个 tab 共用同一套行式条目。
 *
 * tab 用真实链接而不是前端切 DOM：多页形态下 URL 就是状态，刷新/分享都不会错位。
 */

import { api } from './api.js';
import { formatTime, handleApiError, mountShell } from './shell.js';

const PAGE_SIZE = 10;
const params = new URLSearchParams(location.search);
const tab = params.get('tab') === 'hot' ? 'hot' : 'list';

const el = {
    rows: document.querySelector('#rows'),
    empty: document.querySelector('#empty'),
    emptyTitle: document.querySelector('#emptyTitle'),
    emptySub: document.querySelector('#emptySub'),
    emptyAction: document.querySelector('#emptyAction'),
    more: document.querySelector('#moreBtn'),
    side: document.querySelector('#side'),
};

const state = {
    cursor: null,
    hasMore: false,
    loading: false,
};

function truncate(text, max) {
    const flat = String(text || '').replace(/\s+/g, ' ').trim();
    return flat.length > max ? `${flat.slice(0, max)}…` : flat;
}

/** 一条一行：标题一行 + 元信息一行。没有昵称可显，元信息只放时间和赞数。 */
function rowEl(item, rank) {
    const li = document.createElement('li');
    li.className = 'row';

    const link = document.createElement('a');
    link.className = 'row-title';
    link.href = `/topic.html?id=${item.id}`;
    link.textContent = truncate(item.body, 70) || `内容 #${item.id}`;

    const meta = document.createElement('p');
    meta.className = 'row-meta';
    meta.textContent = `${formatTime(item.createdAt)} · ${item.likeCount ?? 0} 赞`;

    if (rank) {
        const order = document.createElement('span');
        order.className = 'rank';
        order.textContent = String(rank);
        li.append(order);
    }

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

/* ---------------- 最新 ---------------- */

async function loadList(reset) {
    if (state.loading) return;
    state.loading = true;
    el.more.disabled = true;
    try {
        const page = await api.feed(reset ? null : state.cursor, PAGE_SIZE);
        state.cursor = page.nextCursor;
        state.hasMore = page.hasMore;
        if (reset) el.rows.replaceChildren();
        page.items.forEach((item) => el.rows.append(rowEl(item)));
        el.more.hidden = !page.hasMore;
        setEmpty(el.rows.children.length === 0);
    } catch (error) {
        handleApiError(error, '加载失败');
        setEmpty(el.rows.children.length === 0);
    } finally {
        state.loading = false;
        el.more.disabled = false;
    }
}

/* ---------------- 热榜 ---------------- */

async function loadHotRows() {
    try {
        const items = await api.hot(20);
        el.rows.replaceChildren(...items.map((item, index) => rowEl(item, index + 1)));
        setEmpty(items.length === 0, '还没有点赞数据', '先在详情页点一个赞，这里就有排名了。');
    } catch (error) {
        handleApiError(error, '热榜加载失败');
        setEmpty(true, '热榜加载失败', '稍后刷新试试。');
    }
}

/* ---------------- 右侧栏 ---------------- */

function panel(title, note) {
    const section = document.createElement('section');
    section.className = 'panel';
    const head = document.createElement('header');
    head.className = 'panel-head';
    const h2 = document.createElement('h2');
    h2.textContent = title;
    head.append(h2);
    if (note) {
        const span = document.createElement('span');
        span.className = 'tiny muted';
        span.textContent = note;
        head.append(span);
    }
    section.append(head);
    return section;
}

async function renderSideHot() {
    const section = panel('热榜 Top 10', '按点赞数');
    const list = document.createElement('ol');
    list.className = 'side-hot';
    section.append(list);
    el.side.append(section);

    try {
        const items = await api.hot(10);
        if (!items.length) {
            const li = document.createElement('li');
            li.className = 'muted tiny';
            li.textContent = '还没有点赞数据';
            list.append(li);
            return;
        }
        const max = Math.max(1, ...items.map((item) => item.likeCount ?? 0));
        items.forEach((item, index) => {
            const li = document.createElement('li');
            const a = document.createElement('a');
            a.href = `/topic.html?id=${item.id}`;
            a.className = 'side-hot-item';

            const order = document.createElement('span');
            order.className = 'rank';
            order.textContent = String(index + 1);

            const text = document.createElement('span');
            text.className = 'side-hot-text';
            text.textContent = truncate(item.body, 16);

            const bar = document.createElement('i');
            bar.className = 'side-hot-bar';
            bar.style.width = `${Math.max(8, Math.round(((item.likeCount ?? 0) / max) * 100))}%`;

            const count = document.createElement('span');
            count.className = 'side-hot-count';
            count.textContent = String(item.likeCount ?? 0);

            a.append(order, text, bar, count);
            li.append(a);
            list.append(li);
        });
    } catch (error) {
        const li = document.createElement('li');
        li.className = 'muted tiny';
        li.textContent = '热榜加载失败';
        list.append(li);
    }
}

function renderSideAbout() {
    const section = panel('关于本站');
    const intro = document.createElement('p');
    intro.className = 'side-text';
    intro.textContent = '用户中心 + 内容社区的展示站：Spring Boot 3 + MyBatis + MySQL 8 + Redis 7。';

    const chips = document.createElement('ul');
    chips.className = 'chips';
    ['JWT + jti 黑名单', '游标分页', 'Redis ZSet 热榜', '固定窗口限流', 'Idempotency-Key'].forEach((text) => {
        const li = document.createElement('li');
        li.textContent = text;
        chips.append(li);
    });

    const note = document.createElement('p');
    note.className = 'side-text muted tiny';
    note.textContent = '匿名可以浏览列表与详情；点赞、发布需要登录。';

    section.append(intro, chips, note);
    el.side.append(section);
}

/* ---------------- 启动 ---------------- */

async function boot() {
    await mountShell({ active: tab });

    document.querySelectorAll('#tabs a').forEach((link) => {
        link.classList.toggle('active', link.dataset.tab === tab);
    });

    if (tab === 'hot') {
        document.title = '社区 · 热榜';
        el.more.hidden = true;
        el.emptyAction.textContent = '去发布';
        await Promise.all([loadHotRows(), Promise.resolve(renderSideAbout())]);
        return;
    }

    document.title = '社区 · 最新';
    el.more.addEventListener('click', () => loadList(false));
    await Promise.all([loadList(true), renderSideHot()]);
}

boot();
