# community

一个「用户中心 + 内容社区」后端服务，用来把 Java 后端的常见工程能力串成一条完整链路：
注册登录 → 鉴权注销 → 内容发布与分页 → 点赞与热榜 → 限流 → 部署。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 21（源码保持 17 兼容语法） |
| 框架 | Spring Boot 3.x（Web / Actuator / Test） |
| 构建 | Maven |
| 存储 | MySQL（业务数据）+ Redis（会话、计数、限流） |
| 接口风格 | REST + 统一响应契约 |

## 目录结构

```
community/
├── src/main/java/com/community/     # 业务代码，按能力分包
│   ├── CommunityApplication.java    # 启动类
│   ├── common/                      # 统一响应、异常、工具
│   ├── auth/                        # 注册 / 登录 / 会话
│   ├── user/                        # 用户资料
│   ├── content/                     # 内容发布与分页
│   └── like/                        # 点赞与热榜
├── src/main/resources/
│   └── application.yml              # 配置入口
├── src/test/java/com/community/     # 测试，与主代码同包结构
├── docs/learning/                   # 学习文档 + PROGRESS.md
├── deploy/                          # 部署脚本 / compose
└── .githooks/commit-msg             # 提交信息校验
```

## 快速开始

前置：JDK 17+、Maven 3.9+。

```bash
# 首次克隆后启用提交信息检查（只需一次）
git config core.hooksPath .githooks

# 启动
mvn spring-boot:run

# 健康检查
curl -s localhost:8080/actuator/health
```

期望输出：`{"status":"UP"}`

## 开发规范

提交信息、分支命名、代码风格见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 学习路线

按步骤推进，进度记录在 [docs/learning/PROGRESS.md](docs/learning/PROGRESS.md)：

| 步骤 | 主题 |
| --- | --- |
| 01 | 工程骨架 |
| 02 | 数据层 |
| 03 | 统一契约 |
| 04 | 注册登录 |
| 05 | 鉴权注销 |
| 06 | 内容分页 |
| 07 | 点赞热榜 |
| 08 | 限流测试 |
| 09 | 部署讲述 |
