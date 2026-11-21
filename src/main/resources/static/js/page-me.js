/**
 * 「我的」页：看资料 + 改昵称 + 退出，加上「我发布的 / 我赞过的」两个 tab。
 */

import { api, setToken } from './api.js';
import { cacheMe, clearMe, formatDate, handleApiError, initial, mountShell, refreshAuthArea, signOut, toast } from './shell.js';
import { createCursorPager } from './paging.js';
import { contentRow } from './ui/content-row.js';

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
    pwdForm: document.querySelector('#pwdForm'),
    oldPassword: document.querySelector('#oldPassword'),
    newPassword: document.querySelector('#newPassword'),
    pwdSave: document.querySelector('#pwdSave'),
    signout: document.querySelector('#signout'),
    rows: document.querySelector('#rows'),
    empty: document.querySelector('#empty'),
    emptyTitle: document.querySelector('#emptyTitle'),
    emptySub: document.querySelector('#emptySub'),
    more: document.querySelector('#moreBtn'),
};

let tab = 'contents';

function paint(me) {
    el.avatar.textContent = initial(me);
    el.name.textContent = me.nickname || me.username;
    el.username.textContent = `@${me.username}`;
    el.createdAt.textContent = formatDate(me.createdAt) || '—';
    el.userId.textContent = String(me.id);
    el.nickname.value = me.nickname || '';
}

async function savePassword(event) {
    event.preventDefault();
    const oldPassword = el.oldPassword.value;
    const newPassword = el.newPassword.value;
    if (!oldPassword) {
        toast('请输入原密码', 'error');
        return;
    }
    if (newPassword.length < 8 || newPassword.length > 64) {
        toast('新密码长度需 8~64 位', 'error');
        return;
    }
    el.pwdSave.disabled = true;
    try {
        await api.changePassword({ oldPassword, newPassword });
        toast('密码已修改');
        // 改密成功后当前 token 已被服务端拉黑：清本地登录态，引导重新登录
        setToken('');
        clearMe();
        const params = new URLSearchParams({ next: '/me.html', hint: '密码已修改，请重新登录' });
        location.href = `/login.html?${params}`;
    } catch (error) {
        handleApiError(error, '修改失败');
        el.pwdSave.disabled = false;
    }
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

function setEmpty(show, title, sub) {
    el.empty.hidden = !show;
    if (title) el.emptyTitle.textContent = title;
    if (sub) el.emptySub.textContent = sub;
}

/* 三个 tab 共用一套分页：我发布的 / 我赞过的 / 我收藏的 */
const pager = createCursorPager({
    container: el.rows,
    more: el.more,
    size: PAGE_SIZE,
    fetchPage: (cursor, size) => (
        tab === 'contents' ? api.myContents(cursor, size)
            : tab === 'likes' ? api.myLikes(cursor, size)
                : api.myBookmarks(cursor, size)
    ),
    renderItem: (item) => contentRow(item),
    onEmpty: ({ error }) => {
        // 失败时保持原有空态文案不动，与改造前一致
        const title = error ? undefined
            : tab === 'contents' ? '还没有发布内容'
                : tab === 'likes' ? '还没有赞过内容' : '还没有收藏内容';
        setEmpty(el.rows.children.length === 0, title, '');
    },
    onError: handleApiError,
    errorText: '加载失败',
});

function switchTab(next) {
    if (tab === next) return;
    tab = next;
    document.querySelectorAll('#meTabs a').forEach((a) => {
        a.classList.toggle('active', a.dataset.tab === tab);
    });
    pager.reset();
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
    el.pwdForm.addEventListener('submit', savePassword);
    el.signout.addEventListener('click', signOut);

    document.querySelectorAll('#meTabs a').forEach((a) => {
        a.addEventListener('click', (event) => {
            event.preventDefault();
            switchTab(a.dataset.tab);
        });
    });
    el.more.addEventListener('click', () => pager.load());
    pager.reset();
}

boot();

