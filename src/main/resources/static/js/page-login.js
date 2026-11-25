/**
 * 登录 / 注册页。两个模式共用一套表单，靠 URL 上的 ?mode=register 记住状态。
 *
 * 429（连续错密码触发限流）时按后端返回的 Retry-After 倒计时，期间禁用提交按钮。
 */

import { api, setToken } from './api.js';
import { clearMe, loadMe, mountShell, toast } from './shell.js';

const params = new URLSearchParams(location.search);
const rawNext = params.get('next') || '/';
// 只接受站内路径，避免 ?next=https://evil 这类开放重定向
const next = rawNext.startsWith('/') && !rawNext.startsWith('//') ? rawNext : '/';
const hint = params.get('hint') || '';

const el = {
    title: document.querySelector('#title'),
    sub: document.querySelector('#sub'),
    form: document.querySelector('#form'),
    msg: document.querySelector('#msg'),
    submit: document.querySelector('#submit'),
    switch: document.querySelector('#switch'),
    nicknameRow: document.querySelector('#nicknameRow'),
    demoTip: document.querySelector('#demoTip'),
    demoFill: document.querySelector('#demoFill'),
    demoFillAdmin: document.querySelector('#demoFillAdmin'),
};

/** 后端校验消息里的英文字段名 → 前端展示的中文名，避免把内部字段名透给用户 */
const FIELD_LABELS = {
    username: '用户名',
    password: '密码',
    nickname: '昵称',
};

function localizeMessage(message) {
    return String(message || '').replace(/\b(username|password|nickname)\b/g, (m) => FIELD_LABELS[m]);
}

let mode = params.get('mode') === 'register' ? 'register' : 'login';
let locked = false;
let timer = 0;

function renderMode() {
    const registering = mode === 'register';
    document.title = registering ? '社区 · 注册' : '社区 · 登录';
    el.title.textContent = registering ? '注册' : '登录';
    el.sub.textContent = hint || (registering
        ? '用户名 4-32 位字母、数字、下划线；密码 8-64 位。注册后自动登录。'
        : '登录后才能发布与点赞。');
    el.nicknameRow.hidden = !registering;
    el.submit.textContent = registering ? '注册并登录' : '登录';
    el.switch.textContent = registering ? '已有账号，去登录' : '没有账号，去注册';
    // 演示账号只在登录模式展示，注册模式不显示
    el.demoTip.hidden = registering;
}

function countdown(seconds) {
    let left = Math.max(1, Number(seconds) || 30);
    locked = true;
    el.submit.disabled = true;
    const paint = () => {
        el.msg.textContent = `请求过于频繁，请 ${left} 秒后再试`;
    };
    paint();
    clearInterval(timer);
    timer = setInterval(() => {
        left -= 1;
        if (left <= 0) {
            clearInterval(timer);
            locked = false;
            el.submit.disabled = false;
            el.msg.textContent = '';
            return;
        }
        paint();
    }, 1000);
}

el.switch.addEventListener('click', () => {
    mode = mode === 'login' ? 'register' : 'login';
    const url = new URL(location.href);
    if (mode === 'register') url.searchParams.set('mode', 'register');
    else url.searchParams.delete('mode');
    history.replaceState(null, '', url);
    el.form.reset();
    el.msg.textContent = '';
    renderMode();
    el.form.elements.username.focus();
});

// 点击演示账号，直接填入用户名与密码
function fillDemo(username) {
    el.form.elements.username.value = username;
    el.form.elements.password.value = 'Passw0rd!2026';
    el.msg.textContent = '';
    el.form.elements.password.focus();
}

el.demoFill.addEventListener('click', () => fillDemo('demo_alice'));
el.demoFillAdmin.addEventListener('click', () => fillDemo('demo_admin'));

el.form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (locked) return;

    const data = new FormData(el.form);
    const username = String(data.get('username') || '').trim();
    const password = String(data.get('password') || '');
    const nickname = String(data.get('nickname') || '').trim();

    el.submit.disabled = true;
    el.msg.textContent = '';
    try {
        if (mode === 'register') {
            const payload = nickname ? { username, password, nickname } : { username, password };
            await api.register(payload);
        }
        const result = await api.login({ username, password });
        setToken(result.token);          // 只存 token，用户信息用 /api/me 单独查
        clearMe();
        await loadMe({ force: true });
        toast(mode === 'register' ? '注册成功，已为你登录' : '登录成功');
        location.href = next;
    } catch (error) {
        if (error.status === 429) {
            countdown(error.retryAfter);
            return;
        }
        el.msg.textContent = localizeMessage((error && error.message) || '操作失败');
        el.submit.disabled = false;
    }
});

async function boot() {
    renderMode();
    await mountShell({ active: '' });
}

boot();
