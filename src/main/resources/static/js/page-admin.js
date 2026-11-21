/**
 * 管理页：按用户 ID 封禁/解封（仅管理员可进）。
 *
 * 管理员身份来自 /api/me 的 role 字段：后端只对本人返回真实角色，
 * 所以这里可以安全地做「管理入口只给管理员看」的前端分流——
 * 真正的权限校验在服务端（requireAdmin），前端隐藏入口只是体验，不是防线。
 */

import { api } from './api.js';
import { handleApiError, mountShell, toast } from './shell.js';
import { stateBlock } from './ui/state-block.js';

const el = {
    form: document.querySelector('#banForm'),
    userId: document.querySelector('#userId'),
    banBtn: document.querySelector('#banBtn'),
    unbanBtn: document.querySelector('#unbanBtn'),
    root: document.querySelector('#adminRoot'),
};

async function submit(event, status) {
    event.preventDefault();
    const id = Number(el.userId.value);
    if (!Number.isInteger(id) || id <= 0) {
        toast('请输入有效的用户 ID', 'error');
        return;
    }
    const label = status === 0 ? '封禁' : '解封';
    const btn = status === 0 ? el.banBtn : el.unbanBtn;
    btn.disabled = true;
    try {
        await api.setUserStatus(id, status);
        toast(`已${label}用户 ${id}`);
        el.userId.value = '';
    } catch (error) {
        handleApiError(error, `${label}失败`);
    } finally {
        btn.disabled = false;
    }
}

async function boot() {
    const me = await mountShell({ active: 'admin' });

    if (!me) {
        const params = new URLSearchParams({ next: '/admin.html', hint: '管理页需要先登录' });
        location.href = `/login.html?${params}`;
        return;
    }
    if (me.role !== 1) {
        stateBlock(el.root, '需要管理员权限', '这个页面只有管理员（role=1）能打开。');
        return;
    }

    el.banBtn.addEventListener('click', (event) => submit(event, 0));
    el.unbanBtn.addEventListener('click', (event) => submit(event, 1));
}

boot();
