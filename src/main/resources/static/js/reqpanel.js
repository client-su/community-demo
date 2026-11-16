/**
 * 右下角悬浮请求日志。
 *
 * 为什么不是独立页面：多页之间是整页刷新，一刷新 DOM 就没了，
 * 所以「跳页也能看到之前的请求」靠的是 sessionStorage 续命 + 每页重新挂载，而不是常驻节点。
 */

import { clearRequestLog, onRequest, readRequestLog } from './api.js';

const OPEN_KEY = 'community.reqpanel.open';

function node(tag, className, text) {
    const el = document.createElement(tag);
    if (className) el.className = className;
    if (text !== undefined) el.textContent = text;
    return el;
}

function clockOf(ts) {
    return new Date(ts).toLocaleTimeString('zh-CN', { hour12: false });
}

export function mountRequestPanel() {
    if (document.querySelector('.reqpanel')) return;

    const root = node('div', 'reqpanel');

    const pill = node('button', 'reqpill');
    pill.type = 'button';
    pill.title = '展开/收起请求记录';
    const dot = node('span', 'dot');
    const label = node('span', 'label', '请求 0');
    pill.append(dot, label);

    const box = node('section', 'reqbox');

    const head = node('header');
    const title = node('strong', '', '请求记录');
    const note = node('span', 'reqnote', '本标签页 · 仅存前端 sessionStorage · 最多 50 条');
    const clearBtn = node('button', 'ghost tiny', '清空');
    clearBtn.type = 'button';
    const closeBtn = node('button', 'ghost icon', '✕');
    closeBtn.type = 'button';
    closeBtn.setAttribute('aria-label', '收起请求记录');
    head.append(title, note, clearBtn, closeBtn);

    const list = node('ol');
    box.append(head, list);
    root.append(pill, box);
    document.body.append(root);

    function render() {
        const entries = readRequestLog();
        list.replaceChildren();

        if (!entries.length) {
            const empty = node('li', 'reqempty', '还没有请求，点一下页面上的按钮试试');
            list.append(empty);
        }

        // 全部渲染（最多 50 条），超出约 10 行后由容器 max-height + 滚动条接管
        const shown = entries.slice().reverse();

        list.append(...shown.map((entry) => {
            const bad = entry.failed || entry.status === 0 || entry.status >= 400 || (entry.code !== undefined && entry.code !== 0);
            const li = node('li', bad ? 'bad' : 'ok');
            const parts = [];
            if (entry.code !== undefined && entry.code !== 0) parts.push(`code=${entry.code}`);
            parts.push(`${entry.ms}ms`);
            if (entry.traceId) parts.push(entry.traceId.slice(0, 8));

            li.append(
                node('span', 't', clockOf(entry.ts)),
                node('span', 'm', `${entry.method} ${entry.path}`),
                node('span', 's', entry.status === 0 ? 'ERR' : String(entry.status)),
                node('span', 'd', parts.join(' · ')),
            );
            return li;
        }));

        // 数量由头部说明与舏囊上的总数展示，不额外占行
        const has429 = entries.some((entry) => entry.status === 429 || entry.code === 42900);
        label.textContent = has429 ? '429' : `请求 ${entries.length}`;
        pill.classList.toggle('alert', has429);
    }

    function setOpen(open) {
        root.classList.toggle('open', open);
        box.hidden = !open;
        pill.hidden = open;
        sessionStorage.setItem(OPEN_KEY, open ? '1' : '0');
        if (open) list.scrollTop = 0;
    }

    pill.addEventListener('click', () => setOpen(true));
    closeBtn.addEventListener('click', () => setOpen(false));
    clearBtn.addEventListener('click', () => {
        clearRequestLog();
        render();
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && root.classList.contains('open')) setOpen(false);
    });

    // 页面内新请求：emit 已先写 sessionStorage，这里直接重渲染即可
    onRequest(render);

    // 整页跳转后第一次挂载：把上一个页面的记录读出来
    render();
    // 默认就是展开的：这是个展示项目，请求记录是第一眼该看到的东西
    setOpen(sessionStorage.getItem(OPEN_KEY) !== '0');
}
