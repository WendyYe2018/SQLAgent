package org.sqlagent.util;

public class SqlCleaner {

    public static String clean(String sql) {

        if (sql == null) {
            return "";
        }

        sql = sql
                .replace("```sql", "")
                .replace("```", "")
                .trim();

        if (sql.startsWith("(")
                && sql.endsWith(")")) {

            sql = sql.substring(
                    1,
                    sql.length() - 1
            );
        }

        return sql.trim();
    }
}