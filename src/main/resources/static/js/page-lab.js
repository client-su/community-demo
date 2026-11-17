/**
 * 工程能力演示页：亲手撞出 429（限流）与 201→200（幂等重放）。
 *
 * 两处演示都复用 api.js，所有请求照常进入右下角请求面板，可现场看 traceId。
 */

import { api } from './api.js';
import { cachedMe, handleApiError, mountShell, requireLogin, toast } from './shell.js';

const el = {
    rateUser: document.querySelector('#rateUser'),
    ratePass: document.querySelector('#ratePass'),
    rateBtn: document.querySelector('#rateBtn'),
    rateOut: document.querySelector('#rateOut'),
    idemBody: document.querySelector('#idemBody'),
    idemKey: document.querySelector('#idemKey'),
    idemBtn: document.querySelector('#idemBtn'),
    idemOut: document.querySelector('#idemOut'),
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

let rateLines = [];
let idemLines = [];

function addLine(out, lines, text) {
    lines.push(text);
    out.textContent = lines.join('\n');
}

/* ---------------- 限流演示 ---------------- */

async function burstRate() {
    el.rateBtn.disabled = true;
    rateLines = [];
    el.rateOut.textContent = '';
    const username = el.rateUser.value.trim();
    const password = el.ratePass.value;

    for (let i = 1; i <= 6; i++) {
        let line;
        try {
            await api.login({ username, password });
            line = `${i}. 200 登录成功（限流按 IP，成功也计数）`;
        } catch (error) {
            if (error.status === 429) {
                line = `${i}. 429 请求过于频繁（${error.retryAfter}s 后重试）`;
            } else {
                line = `${i}. ${error.status} ${error.message}`;
            }
        }
        addLine(el.rateOut, rateLines, line);
        if (i < 6) await sleep(450);
    }
    el.rateBtn.disabled = false;
}

/* ---------------- 幂等演示 ---------------- */

async function publishOnce() {
    if (!cachedMe()) {
        requireLogin('/lab.html', '演示幂等需要先登录');
        return;
    }
    const body = el.idemBody.value.trim();
    const key = el.idemKey.value.trim();
    if (!body) {
        toast('正文不能为空', 'error');
        return;
    }
    if (!/^[A-Za-z0-9_-]{8,64}$/.test(key)) {
        toast('幂等键需 8~64 位字母数字 _ -', 'error');
        return;
    }

    el.idemBtn.disabled = true;
    try {
        const result = await api.publishMeta(body, key);
        const kind = result.status === 201 ? '首次创建' : '幂等重放';
        addLine(el.idemOut, idemLines, `${result.status} · ${kind} · 内容 id = ${result.data.id}`);
        el.idemOut.classList.add('ok');
        el.idemOut.classList.remove('err');
    } catch (error) {
        addLine(el.idemOut, idemLines, `${error.status} ${error.message}`);
        el.idemOut.classList.add('err');
        el.idemOut.classList.remove('ok');
    } finally {
        el.idemBtn.disabled = false;
    }
}

/* ---------------- 启动 ---------------- */

async function boot() {
    await mountShell({ active: 'lab' });
    el.rateBtn.addEventListener('click', burstRate);
    el.idemBtn.addEventListener('click', publishOnce);
}

boot();
