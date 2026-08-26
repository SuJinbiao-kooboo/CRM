import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 临时脚本：导出 crm_supplier / crm_supplier_contact 完整表结构 + 全部 CRM 相关字典数据
 * 输出到 tmp/db_schema_dict.txt (UTF-8)
 */
public class DumpSchema {

    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://42.194.240.191:3306/crm_me?useUnicode=true&useSSL=false&characterEncoding=utf8&serverTimezone=GMT%2B8";
        String user = "root";
        String password = "eke123";
        PrintStream out = new PrintStream(new FileOutputStream("c:/Project/RuoYi-Vue/tmp/db_schema_dict.txt", false), true, "UTF-8");

        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // 1. 两张表完整字段信息
            for (String table : new String[]{"crm_supplier", "crm_supplier_contact"}) {
                out.println("===== 表结构: " + table + " =====");
                try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                        "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, CHARACTER_MAXIMUM_LENGTH, COLUMN_COMMENT "
                        + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='crm_me' AND TABLE_NAME='" + table
                        + "' ORDER BY ORDINAL_POSITION")) {
                    while (rs.next()) {
                        out.printf("  %-30s %-20s null=%-3s default=%-12s maxLen=%-6s comment=%s%n",
                                rs.getString("COLUMN_NAME"), rs.getString("COLUMN_TYPE"),
                                rs.getString("IS_NULLABLE"), nz(rs.getString("COLUMN_DEFAULT")),
                                nz(rs.getString("CHARACTER_MAXIMUM_LENGTH")), nz(rs.getString("COLUMN_COMMENT")));
                    }
                }
                out.println();
            }

            // 2. 所有字典类型
            out.println("===== sys_dict_type 全部字典 =====");
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT dict_id, dict_name, dict_type, status FROM sys_dict_type ORDER BY dict_type")) {
                while (rs.next()) {
                    out.printf("  %-4s %-30s %-40s %s%n", rs.getString("dict_id"),
                            nz(rs.getString("dict_name")), nz(rs.getString("dict_type")), nz(rs.getString("status")));
                }
            }
            out.println();

            // 3. 与 crm/supplier 相关的字典数据
            out.println("===== sys_dict_data 相关字典值 =====");
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT d.dict_type, d.dict_label, d.dict_value, d.list_class, d.status "
                    + "FROM sys_dict_data d JOIN sys_dict_type t ON d.dict_type=t.dict_type "
                    + "WHERE d.dict_type LIKE 'crm%' OR d.dict_type LIKE 'sys_user_sex' "
                    + "ORDER BY d.dict_type, d.dict_sort")) {
                String last = "";
                while (rs.next()) {
                    String t = nz(rs.getString("dict_type"));
                    if (!t.equals(last)) {
                        out.println("  --- " + t + " ---");
                        last = t;
                    }
                    out.printf("    label=%-20s value=%-15s class=%-12s status=%s%n",
                            nz(rs.getString("dict_label")), nz(rs.getString("dict_value")),
                            nz(rs.getString("list_class")), nz(rs.getString("status")));
                }
            }
        }
        System.out.println("done -> tmp/db_schema_dict.txt");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
