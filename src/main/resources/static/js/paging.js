/**
 * 游标分页：把「首屏 / 加载更多 / 重置」这套状态机收到一处。
 *
 * 抽出原因：列表页、我的页、用户主页、详情页评论四处各写了一遍
 * { cursor, hasMore, loading } + replaceChildren + 按钮显隐 + 空态切换。
 *
 * 代际（seq）解决什么：
 * 切 tab 时旧请求可能还在路上。reset 会让 seq 自增，旧响应回来时代际不符
 * 就直接丢弃，不会出现「标签已切到新 tab、列表还停在旧 tab 数据」的情况。
 *
 * 不接管首页热榜：那是无游标的 Top N，一次取完，用不到分页。
 */

export function createCursorPager({
    container,
    more = null,
    size = 10,
    fetchPage,
    renderItem,
    onEmpty = () => {},
    onError = () => {},
    errorText = '加载失败',
}) {
    let cursor = null;
    let hasMore = false;
    let loading = false;
    let seq = 0;

    async function load({ reset = false } = {}) {
        // 追加请求不并发（防连点）；reset 必须能打断在途请求，否则切 tab 会被直接丢掉
        if (!reset && loading) return;

        const mySeq = ++seq;
        loading = true;
        if (more) more.disabled = true;

        try {
            const page = await fetchPage(reset ? null : cursor, size);
            if (mySeq !== seq) return;   // 已被更新的 reset 取代，丢弃这次响应

            cursor = page.nextCursor ?? null;
            hasMore = !!page.hasMore;

            if (reset) container.replaceChildren();
            const offset = container.children.length;
            page.items.forEach((item, index) => {
                container.append(renderItem(item, offset + index));
            });

            if (more) more.hidden = !hasMore;
            onEmpty({ error: null });
        } catch (error) {
            if (mySeq !== seq) return;
            onError(error, errorText);
            onEmpty({ error });
        } finally {
            // 只有最新一代能释放 loading 与按钮，避免旧请求抢先解禁
            if (mySeq === seq) {
                loading = false;
                if (more) more.disabled = false;
            }
        }
    }

    return {
        load,
        reset: () => load({ reset: true }),
        get cursor() {
            return cursor;
        },
        get hasMore() {
            return hasMore;
        },
    };
}
