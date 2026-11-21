/**
 * 列表页：最新（游标分页）/ 热榜（Top 20）两个 tab 共用同一套行式条目。
 *
 * tab 用真实链接而不是前端切 DOM：多页形态下 URL 就是状态，刷新/分享都不会错位。
 */

import { api } from './api.js';
import { handleApiError, mountShell } from './shell.js';
import { createCursorPager } from './paging.js';
import { contentRow } from './ui/content-row.js';
import { truncate } from './text.js';

const PAGE_SIZE = 10;
const params = new URLSearchParams(location.search);
const tab = params.get('tab') === 'hot' ? 'hot' : 'list';
const q = (params.get('q') || '').trim();
const tagId = Number(params.get('tag'));
// 三种数据源共用一套行式条目与游标逻辑：信息流 / 搜索 / 按标签
const mode = q ? 'search' : (Number.isInteger(tagId) && tagId > 0 ? 'tag' : tab);

const el = {
    rows: document.querySelector('#rows'),
    empty: document.querySelector('#empty'),
    emptyTitle: document.querySelector('#emptyTitle'),
    emptySub: document.querySelector('#emptySub'),
    emptyAction: document.querySelector('#emptyAction'),
    more: document.querySelector('#moreBtn'),
    side: document.querySelector('#side'),
    searchForm: document.querySelector('#searchForm'),
    searchInput: document.querySelector('#searchInput'),
    clearSearch: document.querySelector('#clearSearch'),
};

function setEmpty(show, title, sub) {
    el.empty.hidden = !show;
    if (title) el.emptyTitle.textContent = title;
    if (sub) el.emptySub.textContent = sub;
}

/* ---------------- 最新 ---------------- */

/** 三种数据源共用同一套分页：信息流 / 搜索 / 按标签 */
function fetchPage(cursor, size) {
    if (mode === 'search') return api.search(q, cursor, size);
    if (mode === 'tag') return api.tagContents(tagId, cursor, size);
    return api.feed(cursor, size);
}

const pager = createCursorPager({
    container: el.rows,
    more: el.more,
    size: PAGE_SIZE,
    fetchPage,
    renderItem: (item) => contentRow(item, { showAuthor: true }),
    onEmpty: () => setEmpty(el.rows.children.length === 0),
    onError: handleApiError,
    errorText: '加载失败',
});

/* ---------------- 热榜 ---------------- */

async function loadHotRows() {
    try {
        const items = await api.hot(20);
        el.rows.replaceChildren(...items.map((item, index) => contentRow(item, { rank: index + 1, showAuthor: true })));
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

/** 侧边标签面板：点标签跳 /?tag=<id>，与搜索共用同一套行式列表 */
async function renderSideTags() {
    const section = panel('按标签浏览', '预置标签');
    const list = document.createElement('ul');
    list.className = 'chips';
    section.append(list);
    el.side.append(section);

    try {
        const tags = await api.tags();
        if (!tags.length) {
            const li = document.createElement('li');
            li.className = 'muted tiny';
            li.textContent = '暂无标签';
            list.append(li);
            return;
        }
        tags.forEach((tag) => {
            const li = document.createElement('li');
            const a = document.createElement('a');
            a.href = `/?tag=${tag.id}`;
            a.textContent = tag.name;
            if (mode === 'tag' && tag.id === tagId) a.className = 'on';
            li.append(a);
            list.append(li);
        });
    } catch (error) {
        // 标签面板失败不影响主列表
    }
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
        await Promise.all([loadHotRows(), Promise.resolve(renderSideAbout()), renderSideTags()]);
        return;
    }

    if (mode === 'search') {
        document.title = `社区 · 搜索「${q}」`;
        el.searchInput.value = q;
        el.clearSearch.hidden = false;
        setEmpty(false);
    } else if (mode === 'tag') {
        document.title = '社区 · 标签';
        el.clearSearch.hidden = false;
    } else {
        document.title = '社区 · 最新';
    }

    el.searchForm.addEventListener('submit', (event) => {
        event.preventDefault();
        const value = el.searchInput.value.trim();
        location.href = value ? `/?q=${encodeURIComponent(value)}` : '/';
    });

    el.more.addEventListener('click', () => pager.load());
    await Promise.all([pager.reset(), renderSideHot(), renderSideTags()]);
}

boot();
