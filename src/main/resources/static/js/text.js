/**
 * 文本处理小工具。
 *
 * 抽出原因：列表页、我的页、用户主页原来各写了一份 truncate，
 * 三份实现逐字相同，改截断规则时要改三处。
 */

/** 压平空白后按字符数截断，超出补省略号 */
export function truncate(text, max) {
    const flat = String(text || '').replace(/\s+/g, ' ').trim();
    return flat.length > max ? `${flat.slice(0, max)}…` : flat;
}
