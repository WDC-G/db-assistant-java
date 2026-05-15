import java.sql.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;

/**
 * SQL 执行器 - 通过 JDBC 执行数据库查询，支持智能缓存
 * 
 * 用法：java SqlRunner <command> <jdbcUrl> <user> <password> [args...] [options...]
 * 
 * 命令：
 *   execute_sql    执行 SQL 查询
 *   list_tables    列出所有表
 *   describe_table 查看表结构（支持缓存）
 *   list_indexes   查看表索引（支持缓存）
 * 
 * 缓存策略：
 *   - 缓存目录：~/.cache/db-assistant/
 *   - 默认 TTL：5 分钟
 *   - 支持 --no-cache 强制刷新
 *   - 支持 --cache-ttl <分钟> 自定义过期时间
 */
public class SqlRunner {

    private static final int DEFAULT_MAX_ROWS = 200;
    private static final int DEFAULT_TIMEOUT = 30;
    private static final int MAX_TEXT_LENGTH = 500;
    private static final int DEFAULT_CACHE_TTL = 5; // 分钟

    private static boolean readonly = true;
    private static int maxRows = DEFAULT_MAX_ROWS;
    private static int timeout = DEFAULT_TIMEOUT;
    private static String outputFormat = "json";
    private static boolean noCache = false;
    private static int cacheTtl = DEFAULT_CACHE_TTL;

    private static final String CACHE_DIR = System.getProperty("user.home") + "/.cache/db-assistant";

    private static final String[] WRITE_KEYWORDS = {
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", 
        "TRUNCATE", "REPLACE", "MERGE", "GRANT", "REVOKE"
    };

    private static final Set<String> OPTION_FLAGS = new HashSet<>(Arrays.asList(
        "--readonly", "--no-readonly", "--max-rows", "--timeout", "--format", "--no-cache", "--cache-ttl"
    ));

    public static void main(String[] args) {
        if (args.length < 4) {
            printError("参数不足。用法: java SqlRunner <command> <jdbcUrl> <user> <password> [args...] [options...]");
            System.exit(1);
        }

        String command = args[0];
        String jdbcUrl = args[1];
        String user = args[2];
        String password = args[3];

        List<String> commandArgs = new ArrayList<>();
        List<String> options = new ArrayList<>();
        
        for (int i = 4; i < args.length; i++) {
            if (OPTION_FLAGS.contains(args[i]) || (i > 4 && OPTION_FLAGS.contains(args[i-1]) && isNumber(args[i]))) {
                options.add(args[i]);
            } else {
                commandArgs.add(args[i]);
            }
        }

        parseOptions(options);

        String dbType = detectDbType(jdbcUrl);
        String dbKey = extractDbKey(jdbcUrl);

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(jdbcUrl, user, password);
            if (!dbType.equals("mysql") && !dbType.equals("mariadb")) {
                try {
                    conn.setNetworkTimeout(null, timeout * 1000);
                } catch (SQLException ignored) {}
            }
            
            switch (command.toLowerCase()) {
                case "execute_sql":
                    if (commandArgs.isEmpty()) {
                        printError("execute_sql 需要 SQL 参数");
                        System.exit(1);
                    }
                    String sql = String.join(" ", commandArgs);
                    executeSql(conn, sql);
                    break;
                case "list_tables":
                    listTables(conn, dbType, dbKey);
                    break;
                case "describe_table":
                    if (commandArgs.isEmpty()) {
                        printError("describe_table 需要表名参数");
                        System.exit(1);
                    }
                    describeTable(conn, String.join(" ", commandArgs), dbType, dbKey);
                    break;
                case "list_indexes":
                    if (commandArgs.isEmpty()) {
                        printError("list_indexes 需要表名参数");
                        System.exit(1);
                    }
                    listIndexes(conn, String.join(" ", commandArgs), dbType, dbKey);
                    break;
                default:
                    printError("未知命令: " + command + "。支持的命令: execute_sql, list_tables, describe_table, list_indexes");
                    System.exit(1);
            }
        } catch (SQLException e) {
            printError("数据库错误: " + e.getMessage());
            System.exit(1);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    private static boolean isNumber(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String detectDbType(String jdbcUrl) {
        String url = jdbcUrl.toLowerCase();
        if (url.startsWith("jdbc:mysql:")) return "mysql";
        if (url.startsWith("jdbc:mariadb:")) return "mariadb";
        if (url.startsWith("jdbc:postgresql:")) return "postgresql";
        if (url.startsWith("jdbc:sqlserver:")) return "sqlserver";
        if (url.startsWith("jdbc:sqlite:")) return "sqlite";
        if (url.startsWith("jdbc:oracle:")) return "oracle";
        return "unknown";
    }

    private static String extractDbKey(String jdbcUrl) {
        // 从 JDBC URL 提取唯一标识：host:port/database
        String url = jdbcUrl.toLowerCase();
        try {
            if (url.startsWith("jdbc:sqlserver:")) {
                // jdbc:sqlserver://host:port;databaseName=db
                String after = url.substring("jdbc:sqlserver://".length());
                String[] parts = after.split("[;?]");
                String hostPort = parts[0];
                String db = "";
                for (String p : parts) {
                    if (p.toLowerCase().startsWith("databasename=")) {
                        db = p.substring("databasename=".length());
                        break;
                    }
                }
                return hostPort + "-" + db;
            } else if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
                // jdbc:mysql://host:port/database
                String after = url.substring(url.indexOf("://") + 3);
                String[] parts = after.split("[/?]");
                return parts[0] + "-" + (parts.length > 1 ? parts[1] : "");
            } else if (url.startsWith("jdbc:postgresql:")) {
                // jdbc:postgresql://host:port/database
                String after = url.substring("jdbc:postgresql://".length());
                String[] parts = after.split("[/?]");
                return parts[0] + "-" + (parts.length > 1 ? parts[1] : "");
            } else if (url.startsWith("jdbc:sqlite:")) {
                // jdbc:sqlite:/path/to/db
                String path = url.substring("jdbc:sqlite:".length());
                return path.replace("/", "-").replace(":", "-");
            }
        } catch (Exception e) {
            // 解析失败，使用 MD5
        }
        return md5(jdbcUrl);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input.hashCode() + "";
        }
    }

    private static void parseOptions(List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--readonly".equals(arg)) {
                readonly = true;
            } else if ("--no-readonly".equals(arg)) {
                readonly = false;
            } else if ("--max-rows".equals(arg) && i + 1 < args.size()) {
                maxRows = Integer.parseInt(args.get(++i));
            } else if ("--timeout".equals(arg) && i + 1 < args.size()) {
                timeout = Integer.parseInt(args.get(++i));
            } else if ("--format".equals(arg) && i + 1 < args.size()) {
                outputFormat = args.get(++i);
            } else if ("--no-cache".equals(arg)) {
                noCache = true;
            } else if ("--cache-ttl".equals(arg) && i + 1 < args.size()) {
                cacheTtl = Integer.parseInt(args.get(++i));
            }
        }
    }

    private static void executeSql(Connection conn, String sql) throws SQLException {
        if (readonly && isWriteOperation(sql)) {
            printError("只读模式已启用，不允许执行写操作");
            System.exit(1);
        }

        String trimmed = sql.trim().toUpperCase();
        boolean isQuery = trimmed.startsWith("SELECT") || 
                          trimmed.startsWith("SHOW") || 
                          trimmed.startsWith("DESCRIBE") || 
                          trimmed.startsWith("EXPLAIN") ||
                          trimmed.startsWith("PRAGMA") ||
                          trimmed.startsWith("WITH");

        if (isQuery) {
            executeQuery(conn, sql);
        } else {
            executeUpdate(conn, sql);
        }
    }

    private static void listTables(Connection conn, String dbType, String dbKey) throws SQLException {
        String cacheKey = "tables-" + dbKey;
        
        if (!noCache) {
            String cached = readCache(cacheKey);
            if (cached != null) {
                System.out.println(cached);
                return;
            }
        }

        String sql;
        switch (dbType) {
            case "mysql":
            case "mariadb":
                sql = "SHOW TABLES";
                break;
            case "postgresql":
                sql = "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename";
                break;
            case "sqlserver":
                sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
                break;
            case "sqlite":
                sql = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name";
                break;
            case "oracle":
                sql = "SELECT TABLE_NAME FROM USER_TABLES ORDER BY TABLE_NAME";
                break;
            default:
                sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
        }
        
        List<Map<String, Object>> result = executeQueryRaw(conn, sql);
        String json = toJson(result);
        writeCache(cacheKey, json);
        System.out.println(json);
    }

    private static void describeTable(Connection conn, String tableName, String dbType, String dbKey) throws SQLException {
        String cacheKey = "schema-" + dbKey + "-" + tableName;
        
        if (!noCache) {
            String cached = readCache(cacheKey);
            if (cached != null) {
                System.out.println(cached);
                return;
            }
        }

        String sql;
        switch (dbType) {
            case "mysql":
            case "mariadb":
                sql = "DESCRIBE " + tableName;
                break;
            case "postgresql":
                sql = "SELECT column_name, data_type, is_nullable, column_default, character_maximum_length " +
                      "FROM information_schema.columns WHERE table_name = '" + tableName + "' ORDER BY ordinal_position";
                break;
            case "sqlserver":
                sql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, COLUMN_DEFAULT " +
                      "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "' ORDER BY ORDINAL_POSITION";
                break;
            case "sqlite":
                sql = "PRAGMA table_info(" + tableName + ")";
                break;
            case "oracle":
                sql = "SELECT COLUMN_NAME, DATA_TYPE, NULLABLE, DATA_DEFAULT FROM USER_TAB_COLUMNS WHERE TABLE_NAME = '" + tableName + "' ORDER BY COLUMN_ID";
                break;
            default:
                sql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH " +
                      "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "' ORDER BY ORDINAL_POSITION";
        }
        
        List<Map<String, Object>> result = executeQueryRaw(conn, sql);
        String json = toJson(result);
        writeCache(cacheKey, json);
        System.out.println(json);
    }

    private static void listIndexes(Connection conn, String tableName, String dbType, String dbKey) throws SQLException {
        String cacheKey = "indexes-" + dbKey + "-" + tableName;
        
        if (!noCache) {
            String cached = readCache(cacheKey);
            if (cached != null) {
                System.out.println(cached);
                return;
            }
        }

        String sql;
        switch (dbType) {
            case "mysql":
            case "mariadb":
                sql = "SHOW INDEX FROM " + tableName;
                break;
            case "postgresql":
                sql = "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = '" + tableName + "'";
                break;
            case "sqlserver":
                sql = "SELECT i.name AS index_name, c.name AS column_name, i.is_unique " +
                      "FROM sys.indexes i " +
                      "JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id " +
                      "JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id " +
                      "WHERE i.object_id = OBJECT_ID('" + tableName + "') " +
                      "ORDER BY i.name, ic.key_ordinal";
                break;
            case "sqlite":
                sql = "PRAGMA index_list(" + tableName + ")";
                break;
            case "oracle":
                sql = "SELECT INDEX_NAME, COLUMN_NAME, UNIQUENESS FROM USER_IND_COLUMNS WHERE TABLE_NAME = '" + tableName + "'";
                break;
            default:
                printError("list_indexes 不支持当前数据库类型: " + dbType);
                return;
        }
        
        List<Map<String, Object>> result = executeQueryRaw(conn, sql);
        String json = toJson(result);
        writeCache(cacheKey, json);
        System.out.println(json);
    }

    private static void executeQuery(Connection conn, String sql) throws SQLException {
        List<Map<String, Object>> rows = executeQueryRaw(conn, sql);
        printJson(rows);
    }

    private static List<Map<String, Object>> executeQueryRaw(Connection conn, String sql) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.setMaxRows(maxRows);
        stmt.setQueryTimeout(timeout);
        ResultSet rs = stmt.executeQuery(sql);
        ResultSetMetaData md = rs.getMetaData();
        int columnCount = md.getColumnCount();

        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = md.getColumnLabel(i);
                Object value = rs.getObject(i);
                if (value instanceof String) {
                    String str = (String) value;
                    row.put(columnName, str.length() > MAX_TEXT_LENGTH ? str.substring(0, MAX_TEXT_LENGTH) + "..." : str);
                } else {
                    row.put(columnName, value);
                }
            }
            rows.add(row);
            rowCount++;
        }
        rs.close();
        stmt.close();
        
        return rows;
    }

    private static void executeUpdate(Connection conn, String sql) throws SQLException {
        Statement stmt = conn.createStatement();
        int affectedRows = stmt.executeUpdate(sql);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("affectedRows", affectedRows);
        printJson(Collections.singletonList(result));
    }

    private static boolean isWriteOperation(String sql) {
        String trimmed = sql.trim().toUpperCase();
        for (String keyword : WRITE_KEYWORDS) {
            if (trimmed.startsWith(keyword)) {
                return true;
            }
        }
        return false;
    }

    // 缓存相关方法

    private static String readCache(String key) {
        try {
            Path cacheFile = Paths.get(CACHE_DIR, key + ".json");
            Path metaFile = Paths.get(CACHE_DIR, key + ".meta");
            
            if (!Files.exists(cacheFile) || !Files.exists(metaFile)) {
                return null;
            }
            
            // 检查过期时间
            String meta = new String(Files.readAllBytes(metaFile));
            long created = Long.parseLong(meta.trim());
            long now = System.currentTimeMillis();
            long ttlMs = cacheTtl * 60 * 1000L;
            
            if (now - created > ttlMs) {
                // 缓存已过期，删除
                Files.deleteIfExists(cacheFile);
                Files.deleteIfExists(metaFile);
                return null;
            }
            
            return new String(Files.readAllBytes(cacheFile));
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCache(String key, String content) {
        try {
            Path cacheDir = Paths.get(CACHE_DIR);
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
            
            Path cacheFile = Paths.get(CACHE_DIR, key + ".json");
            Path metaFile = Paths.get(CACHE_DIR, key + ".meta");
            
            Files.write(cacheFile, content.getBytes());
            Files.write(metaFile, String.valueOf(System.currentTimeMillis()).getBytes());
        } catch (Exception e) {
            // 缓存写入失败不影响主流程
        }
    }

    private static void printJson(List<Map<String, Object>> data) {
        if ("markdown".equals(outputFormat)) {
            printMarkdown(data);
        } else {
            System.out.println(toJson(data));
        }
    }

    private static void printMarkdown(List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            System.out.println("（无数据）");
            return;
        }
        
        Map<String, Object> first = data.get(0);
        List<String> headers = new ArrayList<>(first.keySet());
        
        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        for (String h : headers) sb.append(h).append(" | ");
        sb.append("\n| ");
        for (int i = 0; i < headers.size(); i++) sb.append("--- | ");
        sb.append("\n");
        
        for (Map<String, Object> row : data) {
            sb.append("| ");
            for (String h : headers) {
                Object val = row.get(h);
                sb.append(val == null ? "null" : escapeMarkdown(String.valueOf(val))).append(" | ");
            }
            sb.append("\n");
        }
        System.out.println(sb.toString());
    }

    private static String toJson(List<Map<String, Object>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < data.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append("  {");
            Map<String, Object> row = data.get(i);
            int j = 0;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (j > 0) sb.append(", ");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\": ");
                sb.append(formatJsonValue(entry.getValue()));
                j++;
            }
            sb.append("}");
        }
        sb.append("\n]");
        return sb.toString();
    }

    private static String formatJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static String escapeMarkdown(String value) {
        return value.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }

    private static void printError(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", message);
        System.out.println(toJson(Collections.singletonList(error)));
    }
}
