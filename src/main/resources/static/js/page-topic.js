/**
 * 详情页：正文 + 时间 + 赞数 + 点赞/取消 + 删除（仅作者自己）。
 *
 * 后端只有「用户 #id」，没有昵称与头像，所以这里不编造，直接显示编号且不可点。
 * 匿名访问时不请求 /like（那个接口仍要求登录，会 401），只渲染详情里带的 likeCount。
 */

import { ApiError, api } from './api.js';
import { authorName, cachedMe, formatTime, handleApiError, mountShell, toast } from './shell.js';
import { stateBlock } from './ui/state-block.js';

const id = Number(new URLSearchParams(location.search).get('id'));
const root = document.querySelector('#topic');

const state = { item: null, liked: false, likeCount: 0, bookmarked: false, commentCount: 0, busy: false };
let likeBtn = null;
let bookmarkBtn = null;

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

function paintBookmark() {
    if (!bookmarkBtn) return;
    bookmarkBtn.classList.toggle('bookmarked', state.bookmarked);
    bookmarkBtn.textContent = state.bookmarked ? '已收藏' : '收藏';
}

async function toggleBookmark() {
    if (state.busy) return;
    if (!cachedMe()) {
        const params = new URLSearchParams({ next: `/topic.html?id=${id}`, hint: '收藏需要先登录' });
        location.href = `/login.html?${params}`;
        return;
    }
    state.busy = true;
    bookmarkBtn.disabled = true;
    try {
        const result = state.bookmarked ? await api.unbookmark(id) : await api.bookmark(id);
        state.bookmarked = result.bookmarked;
        paintBookmark();
    } catch (error) {
        handleApiError(error, '操作失败');
    } finally {
        state.busy = false;
        bookmarkBtn.disabled = false;
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

    const avatar = document.createElement('a');
    avatar.className = 'avatar';
    avatar.href = `/user.html?id=${item.userId}`;
    avatar.textContent = authorName(item).charAt(0).toUpperCase();

    const meta = document.createElement('div');
    meta.className = 'topic-meta';
    const who = document.createElement('a');
    who.className = 'who';
    who.href = `/user.html?id=${item.userId}`;
    who.textContent = authorName(item);
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

    bookmarkBtn = document.createElement('button');
    bookmarkBtn.type = 'button';
    bookmarkBtn.className = 'like bookmark';
    bookmarkBtn.addEventListener('click', toggleBookmark);
    paintBookmark();
    actions.append(bookmarkBtn);

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
        stateBlock(root, '链接里没有有效的内容编号', '地址应形如 /topic.html?id=12', { back: true });
        return;
    }

    root.textContent = '加载中…';

    let item;
    try {
        item = await api.detail(id);
    } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
            stateBlock(root, '内容不存在', '可能已被删除，或者链接里的编号不对。', { back: true });
        } else {
            stateBlock(root, '加载失败', error.message || '稍后刷新试试。', { back: true });
        }
        return;
    }

    state.item = item;
    state.likeCount = item.likeCount ?? 0;
    state.commentCount = item.commentCount ?? 0;
    document.title = `社区 · ${String(item.body || '').slice(0, 20)}`;
    render(item, me);

    // 登录态才查点赞/收藏状态：匿名调这两个接口仍会 401
    if (me) {
        try {
            const result = await api.likeState(id);
            state.liked = result.liked;
            state.likeCount = result.likeCount;
            paintLike();
        } catch (error) {
            // 拿不到点赞态不影响读正文
        }
        try {
            const bookmark = await api.bookmarkState(id);
            state.bookmarked = bookmark.bookmarked;
            paintBookmark();
        } catch (error) {
            // 拿不到收藏态不影响读正文
        }
    }

    await renderComments(me);
}

/* ---------------- 评论 ---------------- */

const commentsRoot = document.querySelector('#comments');
const commentState = { cursor: null, hasMore: false, loading: false };
let commentListEl = null;
let commentCountEl = null;

function commentEl(comment, me) {
    const li = document.createElement('li');
    li.className = 'comment-item';

    const head = document.createElement('p');
    head.className = 'comment-meta';
    const who = document.createElement('a');
    who.className = 'row-author';
    who.href = `/user.html?id=${comment.userId}`;
    who.textContent = comment.nickname || `用户 #${comment.userId}`;
    const time = document.createElement('span');
    time.textContent = formatTime(comment.createdAt);
    head.append(who, document.createTextNode(' · '), time);

    if (me && me.id === comment.userId) {
        const del = document.createElement('button');
        del.type = 'button';
        del.className = 'btn ghost sm';
        del.textContent = '删除';
        del.addEventListener('click', async () => {
            if (!window.confirm('删除这条评论？')) return;
            try {
                await api.removeComment(comment.id);
                li.remove();
                state.commentCount = Math.max(0, state.commentCount - 1);
                if (commentCountEl) commentCountEl.textContent = `评论 ${state.commentCount}`;
                if (!commentListEl.children.length) showNoComments();
            } catch (error) {
                handleApiError(error, '删除失败');
            }
        });
        head.append(del);
    }

    const body = document.createElement('p');
    body.className = 'comment-body';
    body.textContent = comment.body;

    li.append(head, body);
    return li;
}

function showNoComments() {
    if (!commentListEl || commentListEl.children.length) return;
    const li = document.createElement('li');
    li.className = 'muted tiny';
    li.textContent = '还没有评论，来说两句。';
    commentListEl.append(li);
}

async function renderComments(me) {
    commentsRoot.textContent = '';

    const head = document.createElement('header');
    head.className = 'comments-head';
    const h = document.createElement('h2');
    h.className = 'comments-title';
    commentCountEl = h;
    h.textContent = `评论 ${state.commentCount}`;
    head.append(h);
    commentsRoot.append(head);

    commentListEl = document.createElement('ul');
    commentListEl.className = 'comment-list';
    commentsRoot.append(commentListEl);

    const more = document.createElement('button');
    more.type = 'button';
    more.className = 'btn ghost sm';
    more.textContent = '加载更多';
    more.hidden = true;
    commentsRoot.append(more);

    const form = document.createElement('form');
    form.className = 'comment-form';
    if (me) {
        const textarea = document.createElement('textarea');
        textarea.maxLength = 1000;
        textarea.placeholder = '说点什么…（Ctrl / ⌘ + Enter 发送）';
        const send = document.createElement('button');
        send.type = 'submit';
        send.className = 'btn primary sm';
        send.textContent = '发表评论';
        form.append(textarea, send);

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const body = textarea.value.trim();
            if (!body) return;
            send.disabled = true;
            try {
                const created = await api.comment(id, body);
                // 新评论 id 最大，按 id 倒序应插在最前
                commentListEl.querySelector('.muted.tiny')?.remove();
                commentListEl.prepend(commentEl(created, me));
                textarea.value = '';
                state.commentCount += 1;
                if (commentCountEl) commentCountEl.textContent = `评论 ${state.commentCount}`;
            } catch (error) {
                handleApiError(error, '评论失败');
            } finally {
                send.disabled = false;
            }
        });
        textarea.addEventListener('keydown', (event) => {
            if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                event.preventDefault();
                form.requestSubmit();
            }
        });
    } else {
        const login = document.createElement('a');
        login.className = 'btn ghost sm';
        const params = new URLSearchParams({ next: `/topic.html?id=${id}`, hint: '评论需要先登录' });
        login.href = `/login.html?${params}`;
        login.textContent = '登录后可以评论';
        form.append(login);
    }
    commentsRoot.append(form);

    const load = async (reset) => {
        if (commentState.loading) return;
        commentState.loading = true;
        more.disabled = true;
        try {
            const page = await api.comments(id, reset ? null : commentState.cursor, 10);
            commentState.cursor = page.nextCursor;
            commentState.hasMore = page.hasMore;
            if (reset) commentListEl.replaceChildren();
            page.items.forEach((comment) => commentListEl.append(commentEl(comment, me)));
            more.hidden = !page.hasMore;
            if (!commentListEl.children.length) showNoComments();
        } catch (error) {
            handleApiError(error, '评论加载失败');
            if (!commentListEl.children.length) showNoComments();
        } finally {
            commentState.loading = false;
            more.disabled = false;
        }
    };
    more.addEventListener('click', () => load(false));
    await load(true);
}

boot();
