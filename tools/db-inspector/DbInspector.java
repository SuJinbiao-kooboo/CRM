import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库表结构探查工具（开发辅助工具，不参与业务代码）
 *
 * <p>功能：连接 MySQL 数据库，输出表清单、字段结构、索引、外键信息，用于快速了解数据库表结构设计。</p>
 *
 * <p>使用方式：</p>
 * <pre>
 *   编译：javac -encoding UTF-8 -cp mysql-connector-jar DbInspector.java
 *   运行：java -cp .;mysql-connector-jar DbInspector [参数]
 *   Windows 下可直接运行同目录 run.bat（自动编译 + 运行）
 * </pre>
 *
 * <p>支持参数（均可选，缺省时使用项目默认配置）：</p>
 * <pre>
 *   -url       JDBC 连接地址（默认与 ruoyi-admin/src/main/resources/application-druid.yml 主库一致）
 *   -user      数据库账号（默认 root）
 *   -password  数据库密码（默认 eke123）
 *   -db        数据库名（默认 crm_me，覆盖后会自动替换 url 中的库名）
 *   -table     仅探查指定表（如 -table crm_supplier，缺省则探查全部表）
 *   -out       结果输出文件路径（默认 db_schema.txt，UTF-8 编码，避免控制台中文乱码）
 * </pre>
 *
 * <p>示例：</p>
 * <pre>
 *   java -cp .;mysql-connector-jar DbInspector                // 查看全部表结构
 *   java -cp .;mysql-connector-jar DbInspector -table crm_offer // 只看 offer 表
 * </pre>
 */
public class DbInspector {

    /** 默认 JDBC 连接地址（与 application-druid.yml 主库保持一致） */
    private static final String DEFAULT_URL =
            "jdbc:mysql://42.194.240.191:3306/crm_me?useUnicode=true&useSSL=false&characterEncoding=utf8&serverTimezone=GMT%2B8";
    /** 默认数据库账号 */
    private static final String DEFAULT_USER = "root";
    /** 默认数据库密码 */
    private static final String DEFAULT_PASSWORD = "eke123";
    /** 默认数据库名 */
    private static final String DEFAULT_DB = "crm_me";
    /** 默认结果输出文件（UTF-8） */
    private static final String DEFAULT_OUTPUT = "db_schema.txt";

    /** 命令行参数：JDBC 地址 */
    private String url = DEFAULT_URL;
    /** 命令行参数：账号 */
    private String user = DEFAULT_USER;
    /** 命令行参数：密码 */
    private String password = DEFAULT_PASSWORD;
    /** 命令行参数：数据库名 */
    private String db = DEFAULT_DB;
    /** 命令行参数：仅探查的表（空=全部） */
    private String table = null;
    /** 命令行参数：输出文件 */
    private String output = DEFAULT_OUTPUT;

    public static void main(String[] args) throws Exception {
        DbInspector inspector = new DbInspector();
        inspector.parseArgs(args);
        inspector.run();
    }

    /** 解析命令行参数（-key value 形式） */
    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-url":
                    url = args[++i];
                    break;
                case "-user":
                    user = args[++i];
                    break;
                case "-password":
                    password = args[++i];
                    break;
                case "-db":
                    db = args[++i];
                    break;
                case "-table":
                    table = args[++i];
                    break;
                case "-out":
                    output = args[++i];
                    break;
                default:
                    System.out.println("[警告] 忽略未知参数: " + args[i]);
            }
        }
        // 指定了 -db 时，将 url 中库名替换为目标库名（兼容带参数与不带参数的 JDBC 地址）
        if (!DEFAULT_DB.equals(db)) {
            url = url.replaceFirst("/" + DEFAULT_DB + "(\\?|$)", "/" + db + "$1");
        }
    }

    /** 连接数据库并输出表结构信息（结果写入 UTF-8 文件） */
    private void run() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PrintStream out = new PrintStream(new FileOutputStream(output, false), true, "UTF-8")) {

            System.out.println("数据库连接成功，正在探查表结构（" + (table == null ? "全部表" : "表: " + table) + "）...");
            out.println("===== 数据库连接成功: " + conn.getMetaData().getURL() + " =====");
            out.println("数据库产品: " + conn.getMetaData().getDatabaseProductName()
                    + " " + conn.getMetaData().getDatabaseProductVersion());
            out.println("探查时间: " + new java.util.Date());

            // 1. 表清单（含注释、行数、引擎）
            out.println("\n===== 表清单 =====");
            List<String> tables = new ArrayList<>();
            String tableSql = "SELECT TABLE_NAME, TABLE_COMMENT, ENGINE, TABLE_ROWS "
                    + "FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + db + "'"
                    + (table != null ? " AND TABLE_NAME = '" + table + "'" : "")
                    + " ORDER BY TABLE_NAME";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(tableSql)) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    tables.add(name);
                    out.printf("%-40s 行数:%-8s 引擎:%s  注释:%s%n",
                            name, rs.getString("TABLE_ROWS"), rs.getString("ENGINE"), rs.getString("TABLE_COMMENT"));
                }
            }
            if (tables.isEmpty()) {
                out.println("(未找到任何表，请检查 -db / -table 参数是否正确)");
                finish(output);
                return;
            }

            // 2. 各表字段结构
            out.println("\n===== 各表字段结构 =====");
            String colSql = "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, "
                    + "COLUMN_DEFAULT, COLUMN_COMMENT, EXTRA "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + db + "'"
                    + (table != null ? " AND TABLE_NAME = '" + table + "'" : "")
                    + " ORDER BY TABLE_NAME, ORDINAL_POSITION";
            Map<String, List<String[]>> cols = new LinkedHashMap<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(colSql)) {
                while (rs.next()) {
                    cols.computeIfAbsent(rs.getString("TABLE_NAME"), k -> new ArrayList<>())
                            .add(new String[]{rs.getString("COLUMN_NAME"), rs.getString("COLUMN_TYPE"),
                                    rs.getString("IS_NULLABLE"), rs.getString("COLUMN_KEY"),
                                    rs.getString("COLUMN_DEFAULT"), rs.getString("COLUMN_COMMENT"),
                                    rs.getString("EXTRA")});
                }
            }
            for (String t : tables) {
                List<String[]> list = cols.get(t);
                if (list == null) continue;
                out.println("\n--- 表: " + t + " (" + list.size() + " 个字段) ---");
                out.printf("%-4s %-35s %-18s %-5s %-6s %-12s %s%n",
                        "序", "字段名", "类型", "可空", "键", "默认值", "注释");
                int i = 1;
                for (String[] c : list) {
                    out.printf("%-4d %-35s %-18s %-5s %-6s %-12s %s%n",
                            i++, c[0], c[1], "YES".equals(c[2]) ? "是" : "否", c[3],
                            c[4] == null ? "" : c[4], c[5]);
                }
            }

            // 3. 索引清单
            out.println("\n===== 索引清单 =====");
            String idxSql = "SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS COLS, "
                    + "NON_UNIQUE, INDEX_TYPE "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '" + db + "'"
                    + (table != null ? " AND TABLE_NAME = '" + table + "'" : "")
                    + " GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE, INDEX_TYPE ORDER BY TABLE_NAME, INDEX_NAME";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(idxSql)) {
                while (rs.next()) {
                    out.printf("%-40s 索引:%-25s 列:%-40s 唯一:%s 类型:%s%n",
                            rs.getString("TABLE_NAME"), rs.getString("INDEX_NAME"),
                            rs.getString("COLS"), "0".equals(rs.getString("NON_UNIQUE")) ? "是" : "否",
                            rs.getString("INDEX_TYPE"));
                }
            }

            // 4. 外键清单
            out.println("\n===== 外键清单 =====");
            String fkSql = "SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
                    + "FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA = '" + db + "' AND REFERENCED_TABLE_NAME IS NOT NULL"
                    + (table != null ? " AND TABLE_NAME = '" + table + "'" : "")
                    + " ORDER BY TABLE_NAME";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(fkSql)) {
                boolean has = false;
                while (rs.next()) {
                    has = true;
                    out.printf("%s.%s -> %s.%s (%s)%n",
                            rs.getString("TABLE_NAME"), rs.getString("COLUMN_NAME"),
                            rs.getString("REFERENCED_TABLE_NAME"), rs.getString("REFERENCED_COLUMN_NAME"),
                            rs.getString("CONSTRAINT_NAME"));
                }
                if (!has) out.println("(无外键)");
            }
            finish(output);
        }
    }

    /** 提示结果文件位置 */
    private void finish(String outputPath) {
        System.out.println("===== 探查完成，结果已写入: " + new java.io.File(outputPath).getAbsolutePath() + " =====");
    }
}
