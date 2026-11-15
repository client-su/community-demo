# 接口契约

13 个接口。所有路径以 `/api` 开头，请求与响应都是 `application/json`。

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
| `40100` | 401 | 未登录 / 登录已过期 / 缺少 Bearer 令牌 |
| `40101` | 401 | 用户名或密码错误 |
| `40300` | 403 | 没有操作权限 |
| `40400` | 404 | 请求的资源不存在 |
| `40401` | 404 | 内容不存在或已删除 |
| `40500` | 405 | 请求方法不支持 |
| `40900` | 409 | 资源冲突（唯一键冲突兜底） |
| `40901` | 409 | 用户名已被占用 |
| `40902` | 409 | 已定义保留；点赞走幂等路径，当前不会返回该码 |
| `42900` | 429 | 请求过于频繁 |
| `50000` | 500 | 服务器内部错误（不返回内部细节） |

### 1.3 鉴权

除 `register` / `login` 与三个只读内容接口外，其余接口都需要携带：

```
Authorization: Bearer <token>
```

- token 由 `POST /api/auth/login` 下发，默认有效期 **2 小时**
- 登出后该 token 立即失效（jti 进 Redis 黑名单，TTL 等于剩余有效期）
- **带了无效 token 一定 401**，不会静默降级为匿名——否则前端无法判断该不该重新登录

### 1.4 匿名可读（`@OptionalAuth`）

`GET /api/contents`、`GET /api/contents/{id}`、`GET /api/contents/hot` 三个只读接口：

| 请求头 | 行为 |
| --- | --- |
| 无 `Authorization` | 放行，按匿名处理 |
| 有合法 token | 放行，并识别身份 |
| 有非法 / 过期 token | **401** |

### 1.5 traceId

每个响应都带 `X-Trace-Id` 头，对应的值会出现在服务端每一条日志里。

客户端也可以主动传 `X-Trace-Id`（16~32 位小写十六进制）来串联自己的调用链，不合规则由服务端重新生成。

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
  "data": { "id": 1, "username": "demo_alice", "nickname": "Alice", "createdAt": "2026-11-15T19:51:24" }
}
```

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
{ "body": "第一条内容" }
```

响应 `201`（首次）或 `200`（幂等重放）：

```json
{
  "code": 0,
  "message": "成功",
  "data": { "id": 12, "userId": 1, "body": "第一条内容", "likeCount": 0, "createdAt": "2026-11-15T19:51:24" }
}
```

| 错误 | 说明 |
| --- | --- |
| `40000` | 正文为空 / 超过 2000 字；`Idempotency-Key` 格式非法 |
| `40100` | 未登录 |

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
      { "id": 12, "userId": 1, "body": "第一条内容", "likeCount": 0, "createdAt": "2026-11-15T19:51:24" }
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

---

## 五、用 curl 走一遍

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
```
