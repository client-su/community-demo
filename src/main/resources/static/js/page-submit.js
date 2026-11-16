/**
 * 发布页。
 *
 * 幂等键跟着「这一份草稿」走，不跟着「这一次点击」走：
 * 内容没改就复用同一把钥匙，网络抖动重试或手抖双击都只会创建一条。
 */

import { api } from './api.js';
import { handleApiError, mountShell, toast } from './shell.js';

const el = {
    form: document.querySelector('#form'),
    body: document.querySelector('#body'),
    count: document.querySelector('#count'),
    submit: document.querySelector('#submit'),
    hint: document.querySelector('#hint'),
};

let draft = { key: '', body: '' };

function newKey() {
    return crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function keyForDraft(body) {
    if (!draft.key || draft.body !== body) {
        draft = { key: newKey(), body };
    }
    return draft.key;
}

function syncState() {
    const text = el.body.value;
    el.count.textContent = String(text.length);
    el.submit.disabled = !text.trim();
}

async function submit(event) {
    event.preventDefault();
    const body = el.body.value.trim();
    if (!body) return;

    el.submit.disabled = true;
    el.hint.textContent = '发布中…';
    try {
        const created = await api.publish(body, keyForDraft(body));
        toast('已发布');
        location.href = `/topic.html?id=${created.id}`;
    } catch (error) {
        // 失败时保留 draft.key：直接重试仍是同一个幂等键，不会产生第二条
        el.hint.textContent = '发布失败，可直接重试（不会重复创建）';
        handleApiError(error, '发布失败');
        syncState();
    }
}

async function boot() {
    const me = await mountShell({ active: 'submit' });
    if (!me) {
        const params = new URLSearchParams({ next: '/submit.html', hint: '发布需要先登录' });
        location.href = `/login.html?${params}`;
        return;
    }

    el.body.addEventListener('input', syncState);
    el.body.addEventListener('keydown', (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
            event.preventDefault();
            el.form.requestSubmit();
        }
    });
    el.form.addEventListener('submit', submit);
    syncState();
    el.body.focus();
}

boot();
