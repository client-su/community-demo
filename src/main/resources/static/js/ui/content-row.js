/**
 * 内容行条目：列表页 / 热榜 / 我的页 / 用户主页共用的那一行。
 *
 * 三处原来的差异只有两个开关：
 * 1. showAuthor —— 列表页与热榜要显示作者，我的页与用户主页不显示（作者就是当前页主体）；
 * 2. rank —— 热榜要序号，其它场景不要。
 * 所以这里用两个选项覆盖，不另外拆组件。
 *
 * DOM 结构与 className 必须与改造前逐字一致，否则 app.css 的选择器会失配：
 * li.row > [span.rank] + div.row-main > (a.row-title + p.row-meta)
 */

import { authorName, formatTime } from '../shell.js';
import { truncate } from '../text.js';

export function contentRow(item, { rank = 0, showAuthor = false } = {}) {
    const li = document.createElement('li');
    li.className = 'row';

    // 序号必须在 row-main 之前，CSS 依赖这个兄弟顺序
    if (rank) {
        const order = document.createElement('span');
        order.className = 'rank';
        order.textContent = String(rank);
        li.append(order);
    }

    const link = document.createElement('a');
    link.className = 'row-title';
    link.href = `/topic.html?id=${item.id}`;
    link.textContent = truncate(item.body, 70) || `内容 #${item.id}`;

    const meta = document.createElement('p');
    meta.className = 'row-meta';
    if (showAuthor) {
        // 列表页：作者是可点的链接，后面接时间与赞数
        const author = document.createElement('a');
        author.className = 'row-author';
        author.href = `/user.html?id=${item.userId}`;
        author.textContent = authorName(item);

        const time = document.createElement('span');
        time.className = 'row-time';
        time.textContent = formatTime(item.createdAt);

        const stat = document.createElement('span');
        stat.className = 'row-stat';
        stat.textContent = `${item.likeCount ?? 0} 赞`;

        // 普通列表看不到这些 span 的样式差异；热榜用 flex + gap 排版时，
        // 这两个纯文本的「 · 」会被当作空白忽略，正好换成间距
        meta.append(author, ' · ', time, ' · ', stat);
    } else {
        // 我的页 / 用户主页：不重复显示作者，元信息是一整串文本
        meta.textContent = `${formatTime(item.createdAt)} · ${item.likeCount ?? 0} 赞`;
    }

    const main = document.createElement('div');
    main.className = 'row-main';
    main.append(link, meta);
    li.append(main);
    return li;
}
