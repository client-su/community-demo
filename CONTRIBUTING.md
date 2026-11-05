# 贡献指南

## 1. 一次性准备

```bash
git config core.hooksPath .githooks
```

这条命令把 Git 的钩子目录指向仓库内的 `.githooks/`。不执行的话，`commit-msg` 钩子不会生效，
不合规的提交信息会被直接提交进历史。

## 2. 提交信息格式

只接受一种形状：

```
type(scope): 描述
```

- **type**：`feat` | `fix` | `docs` | `refactor` | `test` | `chore` | `perf` | `build` | `revert`
- **scope**：模块名，小写字母 / 数字 / 连字符，例如 `auth`、`content`、`like`、`redis`、`docs`
- **描述**：冒号后空一格，写"做了什么"，不要写"修改了一些东西"

正确示例：

```
feat(auth): 注册接口与 BCrypt 密码存储
fix(content): 修复游标分页在空结果时的越界
docs(learning): 补充 01 工程骨架要点
```

被拒绝的示例（钩子会打印原因并中止提交）：

```
update code              # 没有 type(scope)
feat: 加个功能            # 没有 scope
feat(Auth): 加功能        # scope 必须小写
feat(auth):加功能         # 冒号后必须空一格
```

## 3. 分支命名

| 用途 | 形状 | 例 |
| --- | --- | --- |
| 新功能 | `feat/<scope>-<简述>` | `feat/auth-register` |
| 修复 | `fix/<scope>-<简述>` | `fix/like-overflow` |
| 文档 | `docs/<简述>` | `docs/learning-notes` |

`main` 保持可运行：合并前必须能 `mvn -q clean verify` 通过。

## 4. 代码风格

- 缩进 4 空格（YAML / JSON 为 2 空格），UTF-8，LF 换行，文件末尾留空行 —— 由 `.editorconfig` 约束。
- 包结构按**能力**分（`auth`、`content`、`like`），不按技术层分（不建 `controller/`、`service/` 大杂烩目录）。
- 不提交注释掉的死代码；不提交调试用的 `System.out.println`。
- 每个 public 方法保持"做一件事"，参数超过 3 个考虑引入请求对象。

## 5. 不要提交的东西

`.gitignore` 已覆盖，但要心里有数：

- `target/` 构建产物、`*.log` 日志
- IDE 目录：`.idea/`、`*.iml`、`.vscode/`
- 密钥类：`.env`、`.env.*`、`*.pem`、`*.key`
- 本地数据库文件：`*.db`、`*.sqlite`

如果已经误提交，光写 `.gitignore` 没用，需要 `git rm --cached <file>` 后重新提交。

## 6. 提交前自查

- [ ] `mvn -q clean verify` 通过
- [ ] 提交信息符合 `type(scope): 描述`
- [ ] 没有把密钥、日志、构建产物带上
- [ ] 有对应学习文档或接口说明的更新（如涉及）
