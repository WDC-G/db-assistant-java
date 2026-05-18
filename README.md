<div align="center">

# DB Assistant Skill

> AI Agent Skill for database exploration via JDBC — zero dependency, zero config.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**English** ｜ [中文](README_zh.md)

</div>

## What It Does

DB Assistant gives AI agents the ability to explore and query relational databases directly, without requiring a running server, API proxy, or any middleware.

It ships as a single Java source file (`SqlRunner.java`) that compiles on the fly and runs against any JDBC driver already present in the user's local Maven/Gradle cache. The AI agent reads `SKILL.md`, resolves the database connection info from the project context, and executes SQL queries — all autonomously.

### Features

- **Multi-database**: 20+ databases — MySQL / MariaDB / Oracle / PostgreSQL / Greenplum / GaussDB / SQL Server / SQLite / DB2 / SAP HANA / DM / Kingbase / OceanBase / ClickHouse / Doris / StarRocks / TDSQL / Sybase / TDengine / Hive / Impala / Inceptor / ArgoDB / GBase / NebulaGraph
- **Zero install**: No build tool required — `javac` compiles the single-file tool on the fly
- **Driver auto-discovery**: Locates JDBC driver JARs from `~/.m2` and `~/.gradle` caches
- **Smart caching**: Schema, table list, and index results cached with configurable TTL
- **Read-only by default**: Write operations require explicit user confirmation
- **Safety guards**: Row limit, query timeout, text truncation, SQL injection detection

## How It Works

```
User asks about database
        ↓
AI reads SKILL.md
        ↓
AI resolves connection info (Spring Boot config, .env, or direct input)
        ↓
AI locates JDBC driver JAR from local Maven/Gradle cache
        ↓
javac compiles SqlRunner.java (cached per JDK version)
        ↓
java runs query → JSON output → AI formats as Markdown table
```

## Commands

| Command | Description |
|---|---|
| `execute_sql` | Execute a SQL query |
| `list_tables` | List all tables in the database |
| `describe_table` | Show table schema (columns, types, nullable) |
| `list_indexes` | Show table indexes |

## Output Format

All commands output JSON:

```json
[
  {"id": 1, "name": "Alice", "email": "alice@example.com"},
  {"id": 2, "name": "Bob", "email": "bob@example.com"}
]
```

The AI agent converts this to Markdown tables automatically.

## Options

| Option | Default | Description |
|---|---|---|
| `--readonly` | enabled | Block write operations (INSERT/UPDATE/DELETE/DROP/...) |
| `--no-readonly` | - | Allow write operations (requires user confirmation) |
| `--max-rows` | 200 | Limit result set size |
| `--timeout` | 30s | Query timeout in seconds |
| `--format` | json | Output format: `json` or `markdown` |
| `--no-cache` | - | Force fresh query, ignore cache |
| `--cache-ttl` | 5 min | Cache expiration time |

## Caching

Schema queries (`list_tables`, `describe_table`, `list_indexes`) are cached to avoid repeated database connections:

```
~/.cache/db-assistant/
├── tables-{host}-{db}.json
├── schema-{host}-{db}-{table}.json
└── indexes-{host}-{db}-{table}.json
```

## Supported AI Platforms

- **OpenCode**
- **Claude Code**
- **Cursor**
- **Gemini CLI**
- **Codex**

## Installation

```bash
# Install skill to your agent's skill directory
git clone https://github.com/cycle2zhou/db-assistant-java.git ~/.agents/skills/db-assistant
```

### Prerequisites

- **JDK 8+** (any version, auto-detected via `mise` or `JAVA_HOME`)
- **JDBC driver** in local Maven (`~/.m2`) or Gradle (`~/.gradle`) cache for your database

### JDBC Driver Auto-Discovery

The skill automatically locates driver JARs from your local build tool caches:

#### Relational Databases

| Database | Driver Artifact | Notes |
|---|---|---|
| MySQL | `mysql-connector-java` / `mysql-connector-j` | Compatible with Doris, StarRocks, TDSQL |
| MariaDB | `mariadb-java-client` | |
| Oracle | `ojdbc8` / `ojdbc11` | Supports SERVICE_NAME and SID modes |
| PostgreSQL | `postgresql` | Compatible with Greenplum |
| GaussDB | `gsjdbc4` / `opengauss-jdbc` | |
| SQL Server | `mssql-jdbc` | |
| SQLite | `sqlite-jdbc` | Local file database |
| DB2 | `jcc` / `db2jcc4` | |
| SAP HANA | `ngdbc` | |
| DM (达梦) | `DmJdbcDriver` | |
| Kingbase (人大金仓) | `kingbase8` | |
| OceanBase | `oceanbase-client` | MySQL / Oracle dual-mode |
| GBase 8a | `gbase-connector-java` | |
| GBase 8s | `gbasedbt-sqli` | |

#### Cloud-native & Analytics

| Database | Driver Artifact | Notes |
|---|---|---|
| ClickHouse | `clickhouse-jdbc` | |
| Hive | `hive-jdbc` | Supports ZooKeeper-based HA |
| Impala | `ImpalaJDBC` | Supports NoAuth / LDAP / Kerberos |
| Inceptor / ArgoDB | `inceptor-driver` / `transwarp2-jdbc` | Transwarp platform |
| TDengine | `taos-jdbcdriver` | |

#### Others

| Database | Driver Artifact |
|---|---|
| Sybase | `jconn4` |
| NebulaGraph | `nebula-jdbc` |

No manual download needed — if you've ever used the database in a Java project, the driver is already there.

> **Tip**: Databases sharing the same JDBC URL prefix (e.g., Doris, StarRocks, TDSQL via `jdbc:mysql:`, Greenplum via `jdbc:postgresql:`) can reuse the corresponding driver. They are all covered by the auto-discovery rules above.

## Project Structure

```
db-assistant-java/
├── SKILL.md            # AI agent skill definition
├── lib/
│   └── SqlRunner.java  # Single-file SQL runner (the entire tool)
├── README.md           # English documentation
├── README_zh.md        # Chinese documentation
├── LICENSE
└── .gitignore
```

## License

[MIT](LICENSE)
