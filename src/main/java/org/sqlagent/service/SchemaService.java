package org.sqlagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class SchemaService {

    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Cacheable(value = "schema", key = "'database-schema'")
    public String getSchema() {
        String schemaSql = """
                SELECT
                    TABLE_NAME,
                    COLUMN_NAME,
                    DATA_TYPE,
                    IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                ORDER BY TABLE_NAME,COLUMN_NAME
                """;

        try {
            List<Map<String,Object>> rows = jdbcTemplate.queryForList(schemaSql);
            Map<String,List<String>> tables = new LinkedHashMap<>();
            Map<String,Map<String,String>> columnTypes = new LinkedHashMap<>();

            for (Map<String,Object> row : rows) {
                String table = row.get("TABLE_NAME").toString();
                String column = row.get("COLUMN_NAME").toString();
                String dataType = row.get("DATA_TYPE") == null ? "unknown" : row.get("DATA_TYPE").toString();
                tables.computeIfAbsent(table, k -> new ArrayList<>()).add(column);
                columnTypes.computeIfAbsent(table, k -> new LinkedHashMap<>()).put(column, dataType);
            }

            StringBuilder schema = new StringBuilder();

            for (var entry : tables.entrySet()) {
                String tableName = entry.getKey();
                schema.append("Table: ")
                       .append(tableName)
                       .append("\n");

                String tableComment = getTableComment(tableName);
                if (tableComment != null && !tableComment.isBlank()) {
                   schema.append("Table comment:\n");
                   schema.append("- ").append(tableComment).append("\n");
                }

                schema.append("Columns:\n");
                for (String column : entry.getValue()) {
                   String dataType = columnTypes.getOrDefault(tableName, Map.of()).getOrDefault(column, "unknown");
                   schema.append("- ")
                           .append(column)
                           .append(" (")
                           .append(dataType)
                           .append(")\n");
                }

                String relationships = getTableRelationships(tableName);
                if (!relationships.isBlank()) {
                   schema.append("Relationships:\n");
                   schema.append(relationships);
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
            log.error("查询数据库 schema 失败: {}", schemaSql, e);
            throw e;
        }
    }

    private String getTableComment(String tableName) {
        String commentFromResource = loadResourceComment(tableName);
        String commentFromDatabase = getTableCommentFromDatabase(tableName);
        if (commentFromResource == null && commentFromDatabase == null) {
            return null;
        }
        StringBuilder combined = new StringBuilder();
        if (commentFromResource != null) {
            combined.append(commentFromResource);
        }
        if (commentFromDatabase != null) {
            if (!combined.isEmpty()) {
                combined.append(" ");
            }
            combined.append(commentFromDatabase);
        }
        return combined.toString().isBlank() ? null : combined.toString();
    }

    private String getTableCommentFromDatabase(String tableName) {
        String sql = """
                SELECT CAST(value AS NVARCHAR(MAX)) AS table_comment
                FROM sys.extended_properties
                WHERE major_id = OBJECT_ID(?)
                 AND minor_id = 0
                 AND name = 'MS_Description'
                """;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tableName);
            if (rows.isEmpty()) {
                return null;
            }
            Object value = rows.get(0).get("table_comment");
            return value == null ? null : value.toString();
        } catch (Exception e) {
            log.debug("读取表注释失败，table={}", tableName, e);
            return null;
        }
    }

    private String loadResourceComment(String tableName) {
        try {
            ClassPathResource resource = new ClassPathResource("table/" + tableName + ".txt");
            if (!resource.exists()) {
                return null;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            log.debug("读取 resource 表注释失败，table={}", tableName, e);
            return null;
        }
    }

    public String getTableRelationships(String tableName) {
        String sql = """
                SELECT
                    OBJECT_NAME(fkc.parent_object_id) AS table_name,
                    COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS column_name,
                    OBJECT_NAME(fkc.referenced_object_id) AS referenced_table_name,
                    COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) AS referenced_column_name
                FROM sys.foreign_key_columns fkc
                WHERE OBJECT_NAME(fkc.parent_object_id) = ?
                ORDER BY COL_NAME(fkc.parent_object_id, fkc.parent_column_id)
                """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tableName);
            if (rows.isEmpty()) {
                return "- no foreign keys\n";
            }

            StringBuilder builder = new StringBuilder();
            for (Map<String, Object> row : rows) {
                builder.append("- ")
                        .append(row.get("column_name"))
                        .append(" -> ")
                        .append(row.get("referenced_table_name"))
                        .append(".")
                        .append(row.get("referenced_column_name"))
                        .append("\n");
            }
            return builder.toString();
        } catch (Exception e) {
            log.warn("读取外键关系失败，table={}", tableName, e);
            return "- no foreign keys\n";
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