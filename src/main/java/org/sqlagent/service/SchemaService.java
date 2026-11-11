package org.sqlagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SchemaService {

    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Cacheable(value = "schema", key = "'database-schema'")
    public String getSchema() {

        String sql = """
                SELECT
                    TABLE_NAME,
                    COLUMN_NAME
                FROM INFORMATION_SCHEMA.COLUMNS
                ORDER BY TABLE_NAME,COLUMN_NAME
                """;

        try {
            List<Map<String,Object>> rows =
                    jdbcTemplate.queryForList(sql);

            Map<String,List<String>> tables =
                    new LinkedHashMap<>();

            for (Map<String,Object> row : rows) {
                String table = row.get("TABLE_NAME").toString();
                String column = row.get("COLUMN_NAME").toString();

                tables.computeIfAbsent(table, k -> new ArrayList<>()).add(column);
            }

            StringBuilder schema = new StringBuilder();

            for (var entry : tables.entrySet()) {
                String tableName = entry.getKey();
                schema.append("Table: ")
                        .append(tableName)
                        .append("\n");

                schema.append("Columns:\n");
                for (String column : entry.getValue()) {
                    schema.append("- ")
                            .append(column)
                            .append("\n");
                }

                schema.append("Sample rows (top 10):\n");
                List<Map<String, Object>> sampleRows = getSampleRows(tableName, entry.getValue());
                if (sampleRows.isEmpty()) {
                    schema.append("- no data\n");
                } else {
                    for (Map<String, Object> sampleRow : sampleRows) {
                        schema.append("- ")
                                .append(sampleRow)
                                .append("\n");
                    }
                }

                schema.append("\n");
            }

            return schema.toString();
        } catch (Exception e) {
            log.error("查询数据库 schema 失败: {}", sql, e);
            throw e;
        }
    }

    private List<Map<String, Object>> getSampleRows(String tableName, List<String> columns) {
        try {
            String quotedColumns = String.join(", ", columns);
            String sql = "SELECT TOP 10 " + quotedColumns + " FROM " + tableName;
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.warn("读取表样例数据失败，table={}", tableName, e);
            return List.of();
        }
    }
}