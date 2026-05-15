---
name: db-assistant
description: 通过上下文解析数据库连接信息，使用 JDBC 执行 SQL 查询。支持 MySQL/PostgreSQL/SQLite/SQL Server/MariaDB，自动定位 Maven/Gradle 仓库中的驱动，兼容所有 JDK 版本。
---

# DB Assistant

通过对话上下文解析数据库连接信息，使用项目中已有的 JDBC 驱动执行 SQL 查询。

## 触发条件

当用户提问涉及以下内容时，激活此 Skill：
- 数据库表结构、字段、索引
- 查询数据、查看数据模型
- 需要了解数据库内容的场景
- 明确提到"数据库""SQL""表""查询"等关键词

## 连接信息解析规则

按以下优先级收集数据库连接信息，缓存到当前会话上下文：

### 优先级 1：对话上下文中用户直接提供

识别自然语言中的数据库信息：
- "连接 MySQL localhost:3306/myapp，用户 root，密码 xxx"
- "数据库是 PostgreSQL，地址 db.example.com:5432，用户 admin"
- "这个项目用 SQLite，文件在 ./data/dev.db"

解析模板：
```
<数据库类型> <主机>:<端口>/<数据库名> 用户 <用户名> 密码 <密码>
```

### 优先级 2：Spring Boot 配置文件自动发现

在项目目录中按顺序查找并解析：
1. `application.yml` / `application.yaml`
2. `application.properties`
3. `application-*.yml`（多环境配置，如 `application-dev.yml`）

提取字段：
```yaml
spring.datasource.url: jdbc:<type>://<host>:<port>/<dbname>
spring.datasource.username: <user>
spring.datasource.password: <password>
spring.datasource.driver-class-name: <driver>
```

`${ENV_VAR}` 占位符解析：
- 从项目 `.env` 文件中查找
- 从系统环境变量中读取

### 优先级 3：项目 .env 文件

查找 `DB_*` 或 `DATABASE_*` 开头的变量：
```
DB_TYPE=mysql
DB_HOST=localhost
DB_PORT=3306
DB_NAME=myapp
DB_USER=root
DB_PASSWORD=xxx
```

### 优先级 4：项目 AGENTS.md 数据库段落

查找 `AGENTS.md` 中包含"数据库""Database""DataSource"等关键词的段落。

## JDBC URL 构建规则

根据解析到的信息构建标准 JDBC URL：

| 数据库类型 | JDBC URL 格式 | 默认端口 |
|----------|--------------|---------|
| MySQL | `jdbc:mysql://<host>:<port>/<dbname>` | 3306 |
| MariaDB | `jdbc:mariadb://<host>:<port>/<dbname>` | 3306 |
| PostgreSQL | `jdbc:postgresql://<host>:<port>/<dbname>` | 5432 |
| SQLite | `jdbc:sqlite:<path>` | N/A |
| SQL Server | `jdbc:sqlserver://<host>:<port>;databaseName=<dbname>` | 1433 |

## JDK 版本检测与驱动定位

### JDK 版本检测

```bash
# 获取当前目录配置的 JDK 路径
JAVA_HOME="$(mise where java)"
JAVA_BIN="$JAVA_HOME/bin"
```

### 驱动 JAR 自动定位

按以下顺序搜索，取最新版本：

**MySQL / MariaDB**：
```bash
find ~/.m2/repository/mysql -name "mysql-connector-java-*.jar" ! -name "*sources*" ! -name "*javadoc*" | sort -V | tail -1
find ~/.m2/repository/com/mysql -name "mysql-connector-j-*.jar" ! -name "*sources*" ! -name "*javadoc*" | sort -V | tail -1
find ~/.gradle/caches/modules-2/files-2.1 -name "mysql-connector-*.jar" ! -name "*sources*" ! -name "*javadoc*" | sort -V | tail -1
```

**PostgreSQL**：
```bash
find ~/.m2/repository/org/postgresql -name "postgresql-*.jar" ! -name "*sources*" ! -name "*javadoc*" | sort -V | tail -1
find ~/.gradle/caches/modules-2/files-2.1/org.postgresql -name "postgresql-*.jar" ! -name "*sources*" ! -name "*javadoc*" | sort -V | tail -1
```

**SQLite**：
```bash
find ~/.m2/repository/org/xerial -name "sqlite-jdbc-*.jar" ! -name "*sources*" ! -name "*javadoc*" | sort -V | tail -1
find ~/.gradle/caches/modules-2/files-2.1/org.xerial -name "sqlite-jdbc-*.jar" ! -name "*sources*" ! -name "*javadoc*" | sort -V | tail -1
```

**SQL Server**：
```bash
find ~/.m2/repository/com/microsoft/sqlserver -name "mssql-jdbc-*.jar" ! -name "*sources*" ! -name "*javadoc*" | sort -V | tail -1
find ~/.gradle/caches/modules-2/files-2.1/com.microsoft.sqlserver -name "mssql-jdbc-*.jar" ! -name "*sources*" ! -name "*javadoc*" | sort -V | tail -1
```

## 命令模式（借鉴 DBHub 设计）

SqlRunner 支持 4 种命令模式，类似 DBHub 的 `execute_sql` + `search_objects`：

| 命令             | 用途           | 参数                          | 输出示例                     |
| ---------------- | -------------- | ----------------------------- | ---------------------------- |
| `execute_sql`    | 执行 SQL 查询  | `<jdbcUrl> <user> <pass> <sql>` | `[{"id":1,"name":"Alice"}]`  |
| `list_tables`    | 列出所有表     | `<jdbcUrl> <user> <pass>`       | `[{"table_name":"users"}]`   |
| `describe_table` | 查看表结构     | `<jdbcUrl> <user> <pass> <table>` | `[{"column_name":"id",...}]` |
| `list_indexes`   | 查看表索引     | `<jdbcUrl> <user> <pass> <table>` | `[{"index_name":"pk",...}]`  |

## 智能缓存策略

### 缓存目录

```
~/.cache/db-assistant/
├── schema-192.168.12.122:1433-test-users.json    # 表结构缓存
├── schema-192.168.12.122:1433-test-users.meta    # 缓存元数据（时间戳）
├── tables-192.168.12.122:1433-test.json          # 表列表缓存
└── indexes-192.168.12.122:1433-test-users.json   # 索引缓存
```

### 缓存规则

| 配置项       | 默认值 | 说明                    |
| ------------ | ------ | ----------------------- |
| 缓存目录     | `~/.cache/db-assistant/` | 符合 XDG 缓存规范       |
| 默认 TTL     | 5 分钟 | 缓存过期时间            |
| 缓存键       | `schema-{host}-{db}-{table}` | 唯一标识              |
| 自动清理     | 过期自动删除 | 无需手动维护          |

### 缓存选项

| 选项           | 说明                          | 示例                          |
| -------------- | ----------------------------- | ----------------------------- |
| `--no-cache`   | 强制刷新，不使用缓存          | `describe_table ... --no-cache` |
| `--cache-ttl`  | 自定义缓存过期时间（分钟）    | `describe_table ... --cache-ttl 10` |

### 缓存工作流

```
1. 首次查询 describe_table users → 查询数据库 → 写入缓存
2. 5 分钟内再次查询 → 读取缓存（跳过数据库连接）
3. 5 分钟后查询 → 缓存过期 → 重新查询数据库
4. 用户添加 --no-cache → 始终查询数据库
```

## SQL 执行流程

### 步骤 1：收集连接信息

1. 检查当前会话上下文是否已缓存连接信息
2. 按优先级 1→2→3→4 收集连接信息
3. 构建 JDBC URL
4. 定位驱动 JAR

### 步骤 2：编译并执行（带编译缓存，按 JDK 版本隔离）

```bash
# 获取 JDK 路径和版本
JAVA_HOME="$(mise where java)"
JAVA_BIN="$JAVA_HOME/bin"
JAVA_VERSION=$("$JAVA_BIN/java" -version 2>&1 | head -1 | sed -E 's/.*version "([^"]+)".*/\1/')
# Java 8 格式: 1.8.0_xxx → 8, Java 11+ 格式: 11.0.x → 11
if [[ "$JAVA_VERSION" == 1.* ]]; then
  JDK_MAJOR=$(echo "$JAVA_VERSION" | cut -d. -f2)
else
  JDK_MAJOR=$(echo "$JAVA_VERSION" | cut -d. -f1)
fi

# 编译缓存目录（按 JDK 版本隔离，避免版本不兼容）
CACHE_DIR="/tmp/db-assistant-cache/java-$JAVA_VERSION"
mkdir -p "$CACHE_DIR"
CLASS_FILE="$CACHE_DIR/SqlRunner.class"

# 检查是否需要重新编译
if [ ! -f "$CLASS_FILE" ] || [ ~/.agents/skills/db-assistant/lib/SqlRunner.java -nt "$CLASS_FILE" ]; then
  "$JAVA_BIN/javac" -d "$CACHE_DIR" ~/.agents/skills/db-assistant/lib/SqlRunner.java
fi

# 执行命令（自动使用 Schema 缓存）
"$JAVA_BIN/java" -cp "$CACHE_DIR:$DRIVER_JAR" SqlRunner \
  <command> "$JDBC_URL" "$DB_USER" "$DB_PASSWORD" [args...] \
  --readonly --max-rows 200 --timeout 30

# 强制刷新 Schema 缓存
"$JAVA_BIN/java" -cp "$CACHE_DIR:$DRIVER_JAR" SqlRunner \
  <command> "$JDBC_URL" "$DB_USER" "$DB_PASSWORD" [args...] \
  --readonly --no-cache
```

**编译缓存目录结构**：
```
/tmp/db-assistant-cache/
├── java-8/
│   └── SqlRunner.class      # Java 8 编译版本
├── java-17/
│   └── SqlRunner.class      # Java 17 编译版本
└── java-21/
    └── SqlRunner.class      # Java 21 编译版本
```

### 步骤 3：结果格式化

SqlRunner 输出 JSON 格式，AI 需转换为 Markdown 表格：

```json
[
  {"id": 1, "name": "Alice", "email": "alice@example.com"},
  {"id": 2, "name": "Bob", "email": "bob@example.com"}
]
```

转换为：

| id | name | email |
|---|---|---|
| 1 | Alice | alice@example.com |
| 2 | Bob | bob@example.com |

## 多数据源管理（借鉴 MCP Toolbox 设计）

### 会话级数据源缓存

在当前会话中缓存多个数据源，支持命名和切换：

```
数据源列表：
- primary: jdbc:mysql://localhost:3306/myapp (root@localhost)
- analytics: jdbc:postgresql://db.example.com:5432/analytics (admin@db.example.com)
- local: jdbc:sqlite:./data/dev.db
```

### 切换数据源

用户可通过以下方式切换：
- "切换到 analytics 数据库"
- "在 primary 数据源上执行查询"
- "查看 local 数据库的表"

### 数据源缓存格式

```json
{
  "sources": {
    "primary": {
      "jdbcUrl": "jdbc:mysql://localhost:3306/myapp",
      "user": "root",
      "password": "xxx",
      "driverJar": "/path/to/mysql-connector-j-9.1.0.jar",
      "lastUsed": "2024-01-01T12:00:00Z"
    }
  },
  "currentSource": "primary"
}
```

## 安全规则（借鉴 DBHub + MCP Toolbox）

| 规则         | 默认值 | 说明                                       |
| ------------ | ------ | ------------------------------------------ |
| 只读模式     | 开启   | 仅允许 SELECT/SHOW/DESCRIBE/EXPLAIN/PRAGMA/WITH |
| 最大行数     | 200    | 防止结果集过大                             |
| 查询超时     | 30 秒  | 防止长查询阻塞                             |
| 文本截断     | 500 字符 | 防止大字段占用过多上下文                   |
| 写操作       | 需确认 | 执行 INSERT/UPDATE/DELETE/DROP 前必须获得用户明确授权 |
| 密码处理     | 脱敏   | 日志和上下文中不显示密码原文               |
| SQL 注入检测 | 开启   | 检测 `; DROP`、`UNION SELECT` 等危险模式   |

### SQL 注入检测规则

在执行 SQL 前检查以下模式：
- `;` 后跟写操作（如 `; DROP TABLE`）
- `UNION SELECT` 后跟敏感表（如 `information_schema`）
- `OR 1=1` 等恒真条件
- `LOAD_FILE`、`INTO OUTFILE` 等文件操作

### 写操作流程

1. 用户请求执行写操作
2. AI 确认操作意图："你确认要执行 `UPDATE users SET status='inactive' WHERE id=1` 吗？这将影响 1 行数据。"
3. 用户明确确认后，移除 `--readonly` 参数执行

## 常见查询模板

### Schema 发现工作流（借鉴 DBHub）

```
1. list_tables → 查看所有表
2. describe_table <table> → 查看表结构
3. list_indexes <table> → 查看索引
4. execute_sql "SELECT * FROM <table> LIMIT 10" → 查看示例数据
```

### 按数据库类型的查询差异

**MySQL**：
```sql
SHOW TABLES;
DESCRIBE <table>;
SHOW INDEX FROM <table>;
```

**PostgreSQL**：
```sql
SELECT tablename FROM pg_tables WHERE schemaname = 'public';
SELECT column_name, data_type FROM information_schema.columns WHERE table_name = '<table>';
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = '<table>';
```

**SQL Server**：
```sql
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE';
SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '<table>';
```

**SQLite**：
```sql
SELECT name FROM sqlite_master WHERE type='table';
PRAGMA table_info(<table>);
```

## 错误处理

SqlRunner 输出 `{"error": "错误信息"}` 时：
1. 向用户报告错误
2. 尝试诊断原因（连接失败、表不存在、语法错误等）
3. 提供修复建议

常见错误：
- `Communications link failure` → 数据库未启动或网络不通
- `Access denied` → 用户名或密码错误
- `Table doesn't exist` → 表名错误或数据库选择错误
- `No suitable driver` → 未找到对应的 JDBC 驱动

## 自动修复规则

当连接新数据库时如遇问题（连接失败、驱动不兼容、SQL 方言差异等）：
1. **自动诊断**：分析错误信息，定位问题根因（驱动版本、JDBC URL 参数、SQL 语法、驱动特性等）
2. **自动修复**：直接修改 `SqlRunner.java` 或 `SKILL.md`，无需用户确认
3. **自动编译**：修复后重新编译并验证
4. **记录变更**：修复后向用户报告修改内容及原因

此类修复属于工具链维护，不受"修改>10 行需确认"规则限制。
