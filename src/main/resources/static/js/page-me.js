/**
 * 资料页：只做「看资料 + 改昵称 + 退出」。
 *
 * 没有「我的内容列表」：列表接口不支持按 userId 过滤，前端也不去拉全量再筛。
 */

import { api } from './api.js';
import { cacheMe, formatDate, handleApiError, initial, mountShell, refreshAuthArea, signOut, toast } from './shell.js';

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
};

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
}

boot();
