<div align="center">

# DB Assistant Skill

> AI Agent 数据库探索技能 — 通过 JDBC 直连数据库，零依赖，零配置。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[English](README.md) ｜ **中文**

</div>

## 它能做什么

DB Assistant 让 AI Agent 能够直接探索和查询关系型数据库，无需运行服务器、API 代理或任何中间件。

它只包含一个 Java 源文件（`SqlRunner.java`），运行时即时编译，直接使用用户本地 Maven/Gradle 缓存中已有的 JDBC 驱动。AI Agent 读取 `SKILL.md`，从项目上下文中解析数据库连接信息，自主执行 SQL 查询。

### 特性

- **多数据库**：MySQL / MariaDB / PostgreSQL / SQL Server / SQLite
- **零安装**：无需构建工具 — `javac` 即时编译单文件工具
- **驱动自动发现**：从 `~/.m2` 和 `~/.gradle` 缓存中定位 JDBC 驱动 JAR
- **智能缓存**：Schema、表列表、索引结果带 TTL 缓存
- **默认只读**：写操作需要用户明确确认
- **安全防护**：行数限制、查询超时、文本截断、SQL 注入检测

## 工作原理

```
用户提问关于数据库的问题
        ↓
AI 读取 SKILL.md
        ↓
AI 解析连接信息（Spring Boot 配置、.env 或用户直接输入）
        ↓
AI 从本地 Maven/Gradle 缓存定位 JDBC 驱动
        ↓
javac 编译 SqlRunner.java（按 JDK 版本缓存）
        ↓
java 执行查询 → JSON 输出 → AI 格式化为 Markdown 表格
```

## 命令

| 命令 | 说明 |
|---|---|
| `execute_sql` | 执行 SQL 查询 |
| `list_tables` | 列出数据库中所有表 |
| `describe_table` | 查看表结构（列名、类型、是否可空） |
| `list_indexes` | 查看表索引 |

## 输出格式

所有命令输出 JSON：

```json
[
  {"id": 1, "name": "Alice", "email": "alice@example.com"},
  {"id": 2, "name": "Bob", "email": "bob@example.com"}
]
```

AI Agent 自动将其转换为 Markdown 表格。

## 选项

| 选项 | 默认值 | 说明 |
|---|---|---|
| `--readonly` | 开启 | 阻止写操作（INSERT/UPDATE/DELETE/DROP/...） |
| `--no-readonly` | - | 允许写操作（需要用户确认） |
| `--max-rows` | 200 | 限制结果集大小 |
| `--timeout` | 30秒 | 查询超时时间 |
| `--format` | json | 输出格式：`json` 或 `markdown` |
| `--no-cache` | - | 强制刷新，忽略缓存 |
| `--cache-ttl` | 5分钟 | 缓存过期时间 |

## 缓存

Schema 查询（`list_tables`、`describe_table`、`list_indexes`）会自动缓存，避免重复连接数据库：

```
~/.cache/db-assistant/
├── tables-{host}-{db}.json
├── schema-{host}-{db}-{table}.json
└── indexes-{host}-{db}-{table}.json
```

## 支持的 AI 平台

- **OpenCode**
- **Claude Code**
- **Cursor**
- **Gemini CLI**
- **Codex**

## 安装

```bash
# 克隆到 Agent 的 skill 目录
git clone https://github.com/cycle2zhou/db-assistant-java.git ~/.agents/skills/db-assistant
```

### 前置条件

- **JDK 8+**（任意版本，通过 `mise` 或 `JAVA_HOME` 自动检测）
- 本地 Maven（`~/.m2`）或 Gradle（`~/.gradle`）缓存中有对应数据库的 **JDBC 驱动**

### JDBC 驱动自动发现

Skill 自动从本地构建工具缓存中定位驱动 JAR：

| 数据库 | 驱动包名 |
|---|---|
| MySQL | `mysql-connector-java` / `mysql-connector-j` |
| MariaDB | `mariadb-java-client` |
| PostgreSQL | `postgresql` |
| SQLite | `sqlite-jdbc` |
| SQL Server | `mssql-jdbc` |

无需手动下载 — 只要你之前在 Java 项目中用过该数据库，驱动就已经在本地了。

## 项目结构

```
db-assistant-java/
├── SKILL.md            # AI Agent 技能定义
├── lib/
│   └── SqlRunner.java  # 单文件 SQL 执行器（整个工具就这一个文件）
├── README.md           # 英文说明
├── README_zh.md        # 中文说明
├── LICENSE
└── .gitignore
```

## 许可证

[MIT](LICENSE)
