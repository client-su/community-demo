# 接口契约

29 个接口。所有路径以 `/api` 开头，请求与响应都是 `application/json`。

## 一、通用约定

### 1.1 统一响应体

成功与失败**同一个形状**，客户端只需要一套判断：

```json
{ "code": 0, "message": "成功", "data": { } }
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | `0` 表示成功；非 0 是业务错误码 |
| `message` | string | 可直接展示给用户的文案 |
| `data` | object \| array \| null | 失败时为 `null` |

### 1.2 错误码

编码规则：**5 位 = HTTP 状态码(3 位) + 同族序号(2 位)**。看日志里的 `40901` 就知道该回 409。

| code | HTTP | 含义 |
| --- | --- | --- |
| `0` | 200 | 成功 |
| `40000` | 400 | 参数校验失败（响应 `message` 会指出具体字段） |
| `40001` | 400 | 原密码不正确 |
| `40100` | 401 | 未登录 / 登录已过期 / 缺少 Bearer 令牌 |
| `40101` | 401 | 用户名或密码错误 |
| `40300` | 403 | 没有操作权限 / 账号已被禁用 |
| `40400` | 404 | 请求的资源不存在 |
| `40401` | 404 | 内容不存在或已删除 |
| `40402` | 404 | 评论不存在或已删除 |
| `40403` | 404 | 标签不存在 |
| `40500` | 405 | 请求方法不支持 |
| `40900` | 409 | 资源冲突（唯一键冲突兜底） |
| `40901` | 409 | 用户名已被占用 |
| `40902` | 409 | 已定义保留；点赞走幂等路径，当前不会返回该码 |
| `42900` | 429 | 请求过于频繁 |
| `50000` | 500 | 服务器内部错误（不返回内部细节） |

### 1.3 鉴权

除 `register` / `login` 与标注「匿名可读」的接口外，其余接口都需要携带：

```
Authorization: Bearer <token>
```

- token 由 `POST /api/auth/login` 下发，默认有效期 **2 小时**
- 登出后该 token 立即失效（jti 进 Redis 黑名单，TTL 等于剩余有效期）
- **带了无效 token 一定 401**，不会静默降级为匿名——否则前端无法判断该不该重新登录

### 1.4 匿名可读（`@OptionalAuth`）

只读接口匿名可看（带了合法 token 仍识别身份）：

`GET /api/contents`、`GET /api/contents/{id}`、`GET /api/contents/hot`、`GET /api/contents/search`、
`GET /api/contents/{id}/comments`、`GET /api/tags`、`GET /api/tags/{id}/contents`、
`GET /api/users/{id}`、`GET /api/users/{id}/contents`

| 请求头 | 行为 |
| --- | --- |
| 无 `Authorization` | 放行，按匿名处理 |
| 有合法 token | 放行，并识别身份 |
| 有非法 / 过期 token | **401** |

### 1.5 traceId

每个响应都带 `X-Trace-Id` 头，对应的值会出现在服务端每一条日志里。

客户端也可以主动传 `X-Trace-Id`（8~64 位字母、数字、点、下划线、连字符）来串联自己的调用链，不合规则由服务端重新生成。

### 1.6 限流

仅 `POST /api/auth/login` 有限流：**按 IP，固定窗口 5 次 / 60 秒**。

| 响应头 | 说明 |
| --- | --- |
| `X-RateLimit-Limit` | 窗口内上限 |
| `X-RateLimit-Remaining` | 剩余次数 |
| `Retry-After` | 仅 429 时返回，单位秒 |

### 1.7 幂等（`Idempotency-Key`）

`POST /api/contents` 支持幂等键：

- 格式：字母、数字、下划线、连字符，长度 **8~64**
- 语义：**同一份草稿**复用同一个键；内容改了才换新键
- 首次创建 → `201`；同键重放 → `200`，`data` 是同一条内容（同一个 `id`）
- Redis 记录丢了也不影响正确性，由 MySQL 唯一键 `uk_user_idem (user_id, idem_key)` 兜底

---

## 二、认证

### 2.1 注册

```
POST /api/auth/register
```

请求：

```json
{ "username": "demo_alice", "password": "Passw0rd!2026", "nickname": "Alice" }
```

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `username` | 是 | `[A-Za-z0-9_]{4,32}` |
| `password` | 是 | 8~64 位 |
| `nickname` | 否 | ≤ 32 位 |

响应 `201`：

```json
{ "code": 0, "message": "成功", "data": { "id": 1, "username": "demo_alice", "nickname": "Alice" } }
```

| 错误 | 说明 |
| --- | --- |
| `40000` | 字段不合规，`message` 形如 `username 只能包含字母、数字、下划线，长度 4-32` |
| `40901` | 用户名已被占用 |

### 2.2 登录

```
POST /api/auth/login
```

请求：

```json
{ "username": "demo_alice", "password": "Passw0rd!2026" }
```

响应 `200`：

```json
{
  "code": 0,
  "message": "成功",
  "data": { "token": "eyJhbGciOi...", "tokenType": "Bearer", "expiresAt": "2026-11-15T21:55:12Z" }
}
```

| 错误 | 说明 |
| --- | --- |
| `40101` | 用户名或密码错误（两种失败**文案与耗时一致**，防用户枚举） |
| `42900` | 同一 IP 一分钟内第 6 次，带 `Retry-After` |

### 2.3 登出

```
POST /api/auth/logout
Authorization: Bearer <token>
```

无请求体。响应 `200`，`data` 为 `null`。同一个 token 再次使用会得到 `40100`。

---

## 三、用户

### 3.1 当前用户

```
GET /api/me
Authorization: Bearer <token>
```

响应 `200`：

```json
{
  "code": 0,
  "message": "成功",
  "data": { "id": 1, "username": "demo_alice", "nickname": "Alice", "role": 0, "createdAt": "2026-11-15T19:51:24" }
}
```

`role`：`0` 普通用户 / `1` 管理员。只有 `GET /api/me` 与 `PATCH /api/me` 返回真实值；
`GET /api/users/{id}` 的公开资料恒为 `null`，不把别人的管理身份暴露给任意访客。

### 3.2 更新资料（部分更新）

```
PATCH /api/me
Authorization: Bearer <token>
```

请求（`null` 字段表示不修改）：

```json
{ "nickname": "Alice2" }
```

响应 `200`，返回更新后的 `ProfileVO`。

| 错误 | 说明 |
| --- | --- |
| `40000` | 一个字段都没传 → `至少提供一个要更新的字段`；`nickname` 全空白 → `nickname 不能为空白` |

### 3.3 修改密码

```
PATCH /api/me/password
Authorization: Bearer <token>
```

请求：

```json
{ "oldPassword": "Passw0rd!2026", "newPassword": "NewPassw0rd!2026" }
```

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `oldPassword` | 是 | 非空 |
| `newPassword` | 是 | 8~64 位 |

响应 `200`，`data` 为 `null`。**成功后当前 token 立即失效**（jti 进黑名单），客户端按 `40100` 引导重新登录；其他设备的 token 不受影响。

| 错误 | 说明 |
| --- | --- |
| `40001` | 原密码不正确 |
| `40000` | 新密码不合规 |

### 3.4 公开资料（用户主页）

```
GET /api/users/{id}
```

匿名可看。响应 `200`，`data` 为 `ProfileVO`。

| 错误 | 说明 |
| --- | --- |
| `40400` | 用户不存在（**禁用账号与不存在返回同一个码**，防枚举） |

### 3.5 某人发布的内容

```
GET /api/users/{id}/contents?cursor=&size=
```

匿名可看。响应 `200`，`data` 为 `PageResponse<ContentVO>`，游标分页语义同 `4.2`。

### 3.6 我发布的

```
GET /api/me/contents?cursor=&size=
Authorization: Bearer <token>
```

响应 `200`，`data` 为 `PageResponse<ContentVO>`。身份来自 token。

### 3.7 我赞过的

```
GET /api/me/likes?cursor=&size=
Authorization: Bearer <token>
```

响应 `200`，`data` 为 `PageResponse<ContentVO>`，按点赞先后倒序，游标为点赞记录 id。

---

## 四、内容

### 4.1 发布

```
POST /api/contents
Authorization: Bearer <token>
Idempotency-Key: <可选，8~64 位>
```

请求：

```json
{ "body": "第一条内容", "tagIds": [1, 2] }
```

`tagIds` 可选：绑定的预置标签 id 列表（来自 `GET /api/tags`），不传或传空数组表示不带标签。

响应 `201`（首次）或 `200`（幂等重放）：

```json
{
  "code": 0,
  "message": "成功",
  "data": { "id": 12, "userId": 1, "nickname": "Alice", "body": "第一条内容", "likeCount": 0, "commentCount": 0, "createdAt": "2026-11-15T19:51:24" }
}
```

| 错误 | 说明 |
| --- | --- |
| `40000` | 正文为空 / 超过 2000 字；`Idempotency-Key` 格式非法 |
| `40100` | 未登录 |
| `40403` | `tagIds` 里存在不存在的标签（事务回滚，内容不会创建） |

### 4.2 信息流（游标分页）

```
GET /api/contents?cursor=<上一页的 nextCursor>&size=10
```

| 参数 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `cursor` | 否 | 无 | 不传即第一页 |
| `size` | 否 | 10 | 每页条数 |

响应 `200`：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "items": [
      { "id": 12, "userId": 1, "nickname": "Alice", "body": "第一条内容", "likeCount": 0, "commentCount": 0, "createdAt": "2026-11-15T19:51:24" }
    ],
    "nextCursor": 12,
    "hasMore": true
  }
}
```

- **没有 `total`**：大表 `COUNT(*)` 很贵，且无限流场景下「总数」没有意义
- `nextCursor` 是下一页的游标；`hasMore=false` 时为 `null`
- 游标基于 `id` 倒序，翻页过程中有新增内容也不会重复或漏条

### 4.3 详情

```
GET /api/contents/{id}
```

| 错误 | 说明 |
| --- | --- |
| `40401` | 内容不存在或已删除（**已删除与不存在返回同一个码**，防资源枚举） |

响应 `200`，`data` 为单个 `ContentVO`。命中缓存时不会打库。

### 4.4 删除（软删）

```
DELETE /api/contents/{id}
Authorization: Bearer <token>
```

响应 `200`，`data` 为 `null`。

| 错误 | 说明 |
| --- | --- |
| `40401` | 内容不存在、已删除，或**不是自己的**——三种情况返回完全一致 |

### 4.5 点赞 / 取消 / 查询状态

```
POST   /api/contents/{id}/like      # 点赞
DELETE /api/contents/{id}/like      # 取消
GET    /api/contents/{id}/like      # 当前用户的点赞态
Authorization: Bearer <token>       # 三个都要
```

响应 `200`（三个接口同一个形状）：

```json
{ "code": 0, "message": "成功", "data": { "contentId": 12, "liked": true, "likeCount": 1 } }
```

- 重复点赞**不会**加计数，也不会报错（幂等）
- 取消到 0 之后继续取消不会把计数减成负数
- `GET` 需要身份，因为要回答「**你**赞过没有」；匿名的 `GET` 返回 `40100`

### 4.6 热榜

```
GET /api/contents/hot?limit=10
```

响应 `200`，`data` 是 `ContentVO` 数组，按点赞数倒序，只能读到未删除的内容。

排序来自 Redis ZSet；缓存丢失时会在读取路径上从 MySQL 重建。

### 4.7 搜索

```
GET /api/contents/search?q=关键词&cursor=&size=
```

| 参数 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `q` | 是 | 无 | 关键词，1~64 位，空白会被拒绝 |
| `cursor` | 否 | 无 | 同信息流 |
| `size` | 否 | 10 | 每页条数，上限 50 |

响应 `200`，`data` 为 `PageResponse<ContentVO>`，按 `id` 倒序游标分页，只返回未删除内容。

搜索基于 MySQL FULLTEXT + ngram 分词（2 字切分），单个汉字可能不命中，属预期。

---

## 五、评论

### 5.1 评论列表

```
GET /api/contents/{id}/comments?cursor=&size=
```

匿名可读。响应 `200`，`data` 为 `PageResponse<CommentVO>`：

```json
{ "id": 3, "contentId": 12, "userId": 2, "nickname": "Bob", "body": "写得不错", "createdAt": "2026-11-15T20:00:00" }
```

| 错误 | 说明 |
| --- | --- |
| `40401` | 内容不存在或已删除 |

### 5.2 发评论

```
POST /api/contents/{id}/comments
Authorization: Bearer <token>
```

请求：`{ "body": "写得不错" }`（非空，≤ 1000 字）。响应 `201`，`data` 为 `CommentVO`。

### 5.3 删评论

```
DELETE /api/comments/{id}
Authorization: Bearer <token>
```

只能删自己的。「不存在 / 不是我的 / 已删」统一返回 `40402`（防资源枚举）。

---

## 六、标签

### 6.1 标签列表

```
GET /api/tags
```

匿名可读。响应 `200`，`data` 是 `TagVO` 数组：`{ "id": 1, "name": "技术" }`。

标签是预置数据（技术 / 生活 / 职场 / 求助 / 公告 / 闲聊），不支持用户自建。

### 6.2 按标签读内容

```
GET /api/tags/{id}/contents?cursor=&size=
```

匿名可读。响应 `200`，`data` 为 `PageResponse<ContentVO>`。标签不存在 → `40403`。

---

## 七、收藏

### 7.1 收藏 / 取消 / 状态

```
POST   /api/contents/{id}/bookmark   # 收藏（幂等）
DELETE /api/contents/{id}/bookmark   # 取消（幂等）
GET    /api/contents/{id}/bookmark   # 当前用户收藏态
Authorization: Bearer <token>        # 三个都要
```

响应 `200`（三个接口同一个形状）：

```json
{ "code": 0, "message": "成功", "data": { "contentId": 12, "bookmarked": true } }
```

- 重复收藏不会报错、不会产生重复行；收藏**不计入** `like_count`、**不进**热榜
- 对不存在/已删的内容收藏 → `40401`；取消收藏不要求内容可见（软删后仍可清理收藏）

### 7.2 我收藏的

```
GET /api/me/bookmarks?cursor=&size=
Authorization: Bearer <token>
```

响应 `200`，`data` 为 `PageResponse<ContentVO>`，按收藏先后倒序，游标为收藏记录 id。

---

## 八、管理

### 8.1 封禁 / 解封

```
PUT /api/admin/users/{id}/status
Authorization: Bearer <token>        # 必须是管理员（user.role = 1）
```

请求：`{ "status": 0 }`（0 禁用 / 1 正常）。响应 `200`，`data` 为 `null`。

| 错误 | 说明 |
| --- | --- |
| `40300` | 当前用户不是管理员；或目标是管理员自己（不能封禁自己） |
| `40400` | 目标用户不存在 |
| `40000` | `status` 不是 0 / 1 |

封禁**立即生效**：鉴权路径每个请求都会点查 `user.status`，被封禁用户访问任何需登录接口 → `40300`，登录也 → `40300`。解封即恢复。

演示管理员：`demo_admin`（密码与其他演示账号一致）。

---

## 九、用 curl 走一遍

```bash
BASE=http://localhost:8080

# 1) 注册
curl -s -XPOST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"demo_x","password":"Passw0rd!2026","nickname":"X"}'

# 2) 登录，取出 token
TOKEN=$(curl -s -XPOST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo_x","password":"Passw0rd!2026"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 3) 发布（带幂等键），取出 id —— 首次返回 HTTP 201
ID=$(curl -s -XPOST $BASE/api/contents -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-key-0001' \
  -d '{"body":"来自 curl 的第一条"}' | sed -E 's/.*"id":([0-9]+).*/\1/')
echo "内容 id = $ID"

# 4) 用同一个 Idempotency-Key 再发一次：HTTP 200，data.id 不变
curl -s -o /dev/null -w 'replay http=%{http_code}\n' -XPOST $BASE/api/contents \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-key-0001' -d '{"body":"来自 curl 的第一条"}'

# 5) 匿名读信息流
curl -s "$BASE/api/contents?size=3"

# 6) 点赞（id 用上面抓到的，不要硬编码）
curl -s -XPOST "$BASE/api/contents/$ID/like" -H "Authorization: Bearer $TOKEN"

# 7) 热榜
curl -s "$BASE/api/contents/hot?limit=5"

# 8) 搜索
curl -s "$BASE/api/contents/search?q=第一条"

# 9) 发评论（token 同上）
curl -s -XPOST "$BASE/api/contents/$ID/comments" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"body":"沙发"}'

# 10) 收藏
curl -s -XPOST "$BASE/api/contents/$ID/bookmark" -H "Authorization: Bearer $TOKEN"

# 11) 标签列表
curl -s "$BASE/api/tags"
```
