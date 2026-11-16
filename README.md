# community · 用户中心 + 内容社区

> Java + Spring Boot 3 的内容社区后端：注册登录 → JWT 鉴权 → 内容发布与游标分页 →
> 点赞与 Redis 热榜 → 限流与幂等。
> 附一个同源前端页，clone 下来两条命令就能看到界面，以及每个请求的状态码。

**13 个 REST 接口 · 14 个测试类 / 50 个用例 · 测试跑在真实 MySQL 8 + Redis 7 上，不 mock。**

## 界面

- 首页：匿名可读信息流与热榜；点赞、发布需要登录
- 右下角 **请求日志**：每次调用显示「方法 路径 状态码 traceId 耗时」，可以现场撞出 `429`，
  也能看到幂等重放返回的 `200`

## 三个关键取舍

- **点赞幂等**：不靠「先查再插」，而是唯一键 + `INSERT IGNORE`，只有 `inserted == 1` 才加计数。
  代价：不能拿 Redis `SADD` 的返回值当判据，Redis 只能是加速层。
- **游标分页**：`WHERE id < cursor ORDER BY id DESC`，游标取上一页最后一条 id。
  代价：不能跳页；游标必须严格 `<`，写成 `<=` 会重复。
- **Redis 故障降级**：写路径以 MySQL 为准，Redis 失败只 `log.warn`，点赞仍然成功。
  代价：降级期间热榜与计数缓存短暂不准。

完整 10 条（每条都写了代价）见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 怎么自己验证

```bash
mvn test    # 50 个用例。跑前 content / content_like 必须为空，见「快速开始」末尾的说明
```

```bash
# 1) 登录拿 token（演示账号见「快速开始」）
curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo_alice","password":"Passw0rd!2026"}'
# {"code":0,"message":"成功","data":{"token":"eyJhbGciOiJIUzI1NiJ9..."}}

# 2) 同一分钟连错 6 次：前 5 次 401，第 6 次 429
#    限流按 IP 计，成功登录的那次也算一次
for i in $(seq 6); do
  curl -s -o /dev/null -w "%{http_code} " -X POST localhost:8080/api/auth/login \
    -H 'Content-Type: application/json' -d '{"username":"demo_alice","password":"wrong"}'
done; echo
# 401 401 401 401 401 429
```

## 已知边界

- 只有内容与点赞，**没有评论、搜索、分类 / 标签**
- 分页是游标式，**不支持跳到第 N 页**
- 单机部署（内嵌 Tomcat + 单实例 Redis），**没有 CI、没有压测报告**
- 前端是原生 JS 静态页，只用来展示接口行为，**没有前端测试**
- 详情页作者显示为「用户 #id」：`ContentVO` 不带昵称字段，前端不编造

## 快速开始

前置：JDK 17+、Maven 3.9+、Docker（只用来起 MySQL 与 Redis）。

```bash
docker compose up -d                    # 起 MySQL 8 + Redis 7，首次启动会自动建表
DB_PASSWORD=root mvn spring-boot:run    # 启动应用
# 然后打开 http://localhost:8080
```

> compose 里 MySQL 的 root 密码是 `root`，而 `application.yml` 的默认是空密码，
> 所以第二条要带 `DB_PASSWORD=root`。
> 本机已有 MySQL / Redis 占着默认端口时，看 [docker-compose.yml](docker-compose.yml) 顶部的端口覆盖写法。

JWT 密钥不配也能跑：没设 `JWT_SECRET` 时，启动会生成一把一次性随机密钥（日志打 WARN），
代价是重启后已经发出去的 token 全部失效。对外部署必须显式给一把，至少 32 字节：

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

早期版本的 `application.yml` 里写死过一个默认密钥，它仍留在 git 历史中，本项目没有改写历史；
当前代码不含任何可用的密钥。

想看有内容的界面（4 个用户 / 8 条内容 / 9 条点赞）：

```bash
mysql -uroot -proot -h127.0.0.1 community < deploy/sql/seed-demo.sql
redis-cli DEL hot:content               # 让热榜从 DB 重建
```

演示账号：`demo_alice` / `demo_bob` / `demo_carol` / `demo_dave`，密码都是 `Passw0rd!2026`。

不灌演示数据也能跑完所有功能，首页会显示空态。注意：**演示数据不要带进 `mvn test`**，
分页用例假设 `content` 表为空（所以 seed 刻意没有挂进 `docker-entrypoint-initdb.d`）。

## 接口

13 个接口，完整契约（请求体、响应体、错误码）见 [API.md](API.md)。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 匿名 | 注册，创建成功返回 201 |
| POST | `/api/auth/login` | 匿名 | 登录，按 IP 限流 5 次 / 分钟 |
| POST | `/api/auth/logout` | 需要 | 登出，jti 进黑名单 |
| GET | `/api/me` | 需要 | 当前用户资料 |
| PATCH | `/api/me` | 需要 | 部分更新（只改传了的字段） |
| GET | `/api/contents` | 匿名 | 游标分页信息流 |
| POST | `/api/contents` | 需要 | 发布，支持 `Idempotency-Key` |
| GET | `/api/contents/{id}` | 匿名 | 详情（带缓存） |
| DELETE | `/api/contents/{id}` | 需要 | 软删，只能删自己的 |
| POST | `/api/contents/{id}/like` | 需要 | 点赞，幂等 |
| DELETE | `/api/contents/{id}/like` | 需要 | 取消点赞 |
| GET | `/api/contents/{id}/like` | 需要 | 当前用户的点赞态 |
| GET | `/api/contents/hot` | 匿名 | 热榜 Top N |

统一响应体：`{"code": 0, "message": "成功", "data": ...}`。
`code` 非 0 时是 5 位业务错误码，前三位即对应的 HTTP 状态码（如 `40901` → 409）。

## 目录结构

```
community/
├── src/main/java/com/community/
│   ├── common/          统一响应、错误码、异常、traceId、用户上下文、限流
│   ├── auth/            注册 / 登录 / JWT / 鉴权拦截器 / 登出黑名单
│   ├── user/            用户资料
│   ├── content/         发布、游标分页、详情缓存、软删、热榜
│   └── like/            点赞关系、计数、ZSet 热榜、Redis 降级
├── src/main/resources/
│   ├── application.yml
│   └── static/          前端页（同源托管，零构建）
├── src/test/java/com/community/   14 个测试类 / 50 个用例
├── ARCHITECTURE.md               每条设计决策的问题、方案与代价
├── API.md                        13 个接口的完整契约
├── deploy/sql/
│   ├── schema.sql       建表
│   ├── seed-demo.sql    演示数据
│   └── migrations/      增量迁移
├── docker-compose.yml   只起 MySQL + Redis
└── .githooks/commit-msg 提交信息校验
```

按**能力**分包，不按 `controller/service/mapper` 分层：后者会让一个功能的改动散落在四个目录里。

## 测试

```bash
mvn test
```

14 个类 / 50 个用例，跑在真实的 MySQL + Redis 上（没有把存储 mock 掉）。

| 关注点 | 用例在验什么 |
| --- | --- |
| 密码与 JWT | BCrypt 同密码不同哈希；六种无效 token 全部拒绝 |
| 鉴权边界 | 无 token / 篡改 / 过期 / 黑名单；匿名可读，写接口仍 401 |
| 分页与缓存 | 游标三页无重复；缓存 miss → hit；空值标记挡住穿透 |
| 点赞与热榜 | 重复点赞计数不变；ZSet 与 SQL 排序一致；缓存清空后自动重建 |
| 并发与降级 | 50 线程并发点赞只落 1 行；Redis 全挂时点赞仍成功 |
| 端到端 | 注册 → 登录 → 发布（幂等）→ 列表 → 详情 → 删除 → 404 |

## 技术栈

Java 21 / Spring Boot 3.5 / MyBatis（注解 Mapper）/ MySQL 8 / Redis 7 / JJWT + BCrypt /
原生 JS 静态页（零构建、同源托管）。每一层的选型理由见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 开发规范

提交信息只接受 `type(scope): 描述`，由 `.githooks/commit-msg` 校验。详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

```bash
git config core.hooksPath .githooks   # 首次 clone 后执行一次
```

## License

[MIT](LICENSE)。这是一个学习 / 求职用的项目，不建议直接上生产。
