/**
 * 整页状态块：加载失败 / 内容不存在 / 无权限时替换掉的整块提示。
 *
 * 原来详情页与管理页各写了一份，差别只有「要不要给一个回列表的按钮」，
 * 这里用 back 选项覆盖。
 */

export function stateBlock(root, title, sub, { back = false } = {}) {
    const box = document.createElement('div');
    box.className = 'state';

    const h = document.createElement('p');
    h.className = 'state-title';
    h.textContent = title;

    const p = document.createElement('p');
    p.className = 'state-sub';
    p.textContent = sub;

    box.append(h, p);

    if (back) {
        const link = document.createElement('a');
        link.className = 'btn ghost';
        link.href = '/';
        link.textContent = '回到列表';
        box.append(link);
    }

    root.replaceChildren(box);
}
