package org.sqlagent.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SchemaVectorService {

    private static final String COLLECTION_NAME = "Taobao";
    private static final String DATABASE_NAME = "Taobao";

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    @Value("${qdrant.base-url:http://localhost:6333}")
    private String qdrantBaseUrl;

    private final JdbcTemplate jdbcTemplate;
    private final SchemaService schemaService;
    private final RestTemplate restTemplate = new RestTemplate();

    public SchemaVectorService(JdbcTemplate jdbcTemplate, SchemaService schemaService) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaService = schemaService;
    }

    @PostConstruct
    public void initializeSchemaCollection() {
        try {
            seedTableMetadata();
        } catch (Exception e) {
            log.warn("初始化 Taobao 数据库 schema 向量失败，应用仍可正常运行，稍后可手动重试。", e);
        }
    }

    public String findRelevantSchema(String question) {
        if (question == null || question.isBlank()) {
            return schemaService.getSchema();
        }

        List<Double> vector = embed(question);
        if (vector == null || vector.isEmpty()) {
            return schemaService.getSchema();
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("vector", vector);
            body.put("limit", 3);
            body.put("with_payload", true);

            Map<String, Object> response = restTemplate.postForObject(
                    qdrantBaseUrl + "/collections/" + COLLECTION_NAME + "/points/search",
                    body,
                    Map.class
            );

            if (response == null || response.get("result") == null) {
                return schemaService.getSchema();
            }

            List<Object> results = (List<Object>) response.get("result");
            StringBuilder builder = new StringBuilder();
            for (Object item : results) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Map<?, ?> payload = (Map<?, ?>) entry.get("payload");
                if (payload == null) {
                    continue;
                }
                Object tableName = payload.get("table");
                Object document = payload.get("document");
                if (tableName != null && document != null) {
                    builder.append("Table: ")
                            .append(tableName)
                            .append("\n")
                            .append(document)
                            .append("\n\n");
                }
            }

            if (builder.isEmpty()) {
                return schemaService.getSchema();
            }
            return builder.toString();
        } catch (Exception e) {
            log.warn("从 Qdrant 检索相关 schema 失败，回退到全量 schema。question={}", question, e);
            return schemaService.getSchema();
        }
    }

    public void seedTableMetadata() {
        List<String> tables = getTableNames();
        if (tables.isEmpty()) {
            log.info("未发现数据库表，跳过 schema 向量写入。");
            return;
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (String tableName : tables) {
            try {
                String document = buildTableDocument(tableName);
                if (document == null || document.isBlank()) {
                    continue;
                }
                List<Double> vector = embed(document);
                if (vector == null || vector.isEmpty()) {
                    log.warn("表 {} embedding 失败，跳过写入。", tableName);
                    continue;
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("database", DATABASE_NAME);
                payload.put("table", tableName);
                payload.put("source", "schema_metadata");
                payload.put("document", document);
                payload.put("created_at", Instant.now().toString());

                Map<String, Object> point = new HashMap<>();
                point.put("id", UUID.randomUUID().toString());
                point.put("vector", vector);
                point.put("payload", payload);
                points.add(point);
            } catch (Exception e) {
                log.warn("表 {} schema 向量处理失败，忽略继续处理其他表。", tableName, e);
            }
        }

        if (points.isEmpty()) {
            return;
        }

        ensureCollection(points.get(0).get("vector") instanceof List<?> list ? ((List<?>) list).size() : 3);
        upsertPoints(points);
        log.info("已写入 {} 个表的 schema metadata 到 Qdrant collection {}。", points.size(), COLLECTION_NAME);
    }

    private List<String> getTableNames() {
        String sql = """
                SELECT TABLE_NAME
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;
        return jdbcTemplate.queryForList(sql, String.class);
    }

    private String buildTableDocument(String tableName) {
        StringBuilder document = new StringBuilder();
        document.append("Database: ").append(DATABASE_NAME).append("\n");
        document.append("Table: ").append(tableName).append("\n");

        String resourceComment = readTableCommentFromResource(tableName);
        if (resourceComment != null && !resourceComment.isBlank()) {
            document.append("Comment:\n").append(resourceComment).append("\n");
        }

        String databaseComment = readDatabaseCommentFromResource();
        if (databaseComment != null && !databaseComment.isBlank()) {
            document.append("Database context:\n").append(databaseComment).append("\n");
        }

        List<String> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                String.class,
                tableName
        );
        if (!columns.isEmpty()) {
            document.append("Columns:\n");
            for (String column : columns) {
                document.append("- ").append(column).append("\n");
            }
        }

        String relationships = schemaService.getTableRelationships(tableName);
        if (relationships != null && !relationships.isBlank()) {
            document.append("Relationships:\n");
            document.append(relationships);
        }

        return document.toString().trim();
    }

    private String readDatabaseCommentFromResource() {
        try {
            ClassPathResource resource = new ClassPathResource("rag/taobao.txt");
            if (!resource.exists()) {
                return null;
            }
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.debug("读取数据库注释资源失败，database={}", DATABASE_NAME, e);
            return null;
        }
    }

    private String readTableCommentFromResource(String tableName) {
        try {
            ClassPathResource resource = new ClassPathResource("rag/" + tableName + ".txt");
            if (!resource.exists()) {
                return null;
            }
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.debug("读取表注释资源失败，table={}", tableName, e);
            return null;
        }
    }

    private List<Double> embed(String text) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", embeddingModel);
        request.put("input", text);

        Map<String, Object> response = restTemplate.postForObject(
                ollamaBaseUrl + "/api/embed",
                request,
                Map.class
        );
        if (response == null) {
            return List.of();
        }

        Object embeddings = response.get("embeddings");
        if (!(embeddings instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }

        Object firstEmbedding = list.get(0);
        if (!(firstEmbedding instanceof List<?> values)) {
            return List.of();
        }

        List<Double> vector = new ArrayList<>();
        for (Object value : values) {
            vector.add(((Number) value).doubleValue());
        }
        return vector;
    }

    private void ensureCollection(int vectorSize) {
        try {
            deleteCollectionIfExists();
            Map<String, Object> body = new HashMap<>();
            Map<String, Object> vectors = new HashMap<>();
            vectors.put("size", vectorSize);
            vectors.put("distance", "Cosine");
            body.put("vectors", vectors);
            restTemplate.put(qdrantBaseUrl + "/collections/" + COLLECTION_NAME, body);
            log.info("Qdrant collection {} 已按最新 schema 重建。", COLLECTION_NAME);
        } catch (HttpClientErrorException.Conflict conflict) {
            log.info("Qdrant collection {} 已存在，已按重建策略处理。", COLLECTION_NAME);
        } catch (Exception e) {
            log.debug("确保 Qdrant collection {} 失败，通常说明 Qdrant 未启动。collection={}", COLLECTION_NAME, COLLECTION_NAME, e);
        }
    }

    private void deleteCollectionIfExists() {
        try {
            restTemplate.delete(qdrantBaseUrl + "/collections/" + COLLECTION_NAME);
            log.info("已删除旧的 Qdrant collection {}，准备重建。", COLLECTION_NAME);
        } catch (HttpClientErrorException.NotFound ignored) {
            // collection does not exist yet
        } catch (Exception e) {
            log.debug("删除旧 Qdrant collection {} 失败，继续重建。collection={}", COLLECTION_NAME, COLLECTION_NAME, e);
        }
    }

    private void upsertPoints(List<Map<String, Object>> points) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("points", points);
            restTemplate.put(
                    qdrantBaseUrl + "/collections/" + COLLECTION_NAME + "/points?wait=true",
                    request,
                    Map.class
            );
        } catch (Exception e) {
            log.warn("写入 schema 向量到 Qdrant 失败，collection={}", COLLECTION_NAME, e);
        }
    }
}
