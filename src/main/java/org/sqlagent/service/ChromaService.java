package org.sqlagent.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChromaService {

    private static final Logger log = LoggerFactory.getLogger(ChromaService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${chroma.url:http://127.0.0.1:8000}")
    private String chromaUrl;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    private String tenant = "default_tenant";
    private String databaseName = "default_database";

    @PostConstruct
    public void init() {
        this.databaseName = extractDatabaseName(datasourceUrl);
        if (databaseName == null || databaseName.isBlank()) {
            this.databaseName = "Taobao";
        }
        this.tenant = "default_tenant";
        ensureTenant(tenant);
        ensureDatabase(tenant, databaseName);
    }

    public String getCollectionPrefix() {
        return databaseName.toLowerCase();
    }

    public String getCollectionName(String suffix) {
        return getCollectionPrefix() + "_" + suffix;
    }

    private String getCollectionsBaseUrl() {
        return chromaUrl + "/api/v2/tenants/" + tenant + "/databases/" + databaseName + "/collections";
    }

    private void ensureTenant(String tenantName) {
        String tenantUrl = chromaUrl + "/api/v2/tenants";
        try {
            Map<?, ?> tenant = restTemplate.getForObject(tenantUrl + "/" + tenantName, Map.class);
            if (tenant != null) {
                log.info("Chroma tenant exists: {}", tenantName);
                return;
            }
        } catch (Exception e) {
            log.info("Chroma tenant not found, creating: {}", tenantName);
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", tenantName);
            restTemplate.postForEntity(tenantUrl, payload, Map.class);
            log.info("Chroma tenant created: {}", tenantName);
        } catch (Exception ex) {
            log.warn("创建 Chroma tenant 失败，tenant={}, error={}", tenantName, ex.getMessage(), ex);
        }
    }

    private void ensureDatabase(String tenantName, String dbName) {
        String url = chromaUrl + "/api/v2/tenants/" + tenantName + "/databases";
        try {
            Map<?, ?> db = restTemplate.getForObject(url + "/" + dbName, Map.class);
            if (db != null) {
                log.info("Chroma database exists: {}", dbName);
                return;
            }
        } catch (Exception e) {
            log.info("Chroma database not found, creating: {}", dbName);
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", dbName);
            restTemplate.postForEntity(url, payload, Map.class);
            log.info("Chroma database created: {}", dbName);
        } catch (Exception ex) {
            log.warn("创建 Chroma database 失败，tenant={}, database={}, error={}", tenantName, dbName, ex.getMessage(), ex);
        }
    }

    public void ensureCollection(String name) {
        String url = getCollectionsBaseUrl();
        try {
            Map<?, ?> collection = restTemplate.getForObject(url + "?limit=100&offset=0", Map.class);
            if (collection != null && collection.get("collections") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map && name.equals(String.valueOf(map.get("name")))) {
                        log.info("Chroma collection exists: {}", name);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.info("Chroma collection not found or list API unavailable, creating: {}", name);
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("metadata", Map.of("hnsw:space", "cosine"));
            payload.put("get_or_create", true);
            Map<String, Object> result = restTemplate.postForObject(url, payload, Map.class);
            log.info("Chroma collection created: {} -> {}", name, result);
        } catch (Exception createEx) {
            log.warn("创建 Chroma collection 失败，collection={}, url={}, error={}", name, url, createEx.getMessage(), createEx);
        }
    }

    public void addDocuments(String collectionName, List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        ensureCollection(collectionName);

        String collectionId = findCollectionId(collectionName);
        if (collectionId == null || collectionId.isBlank()) {
            log.warn("未找到 Chroma collection，collection={}", collectionName);
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("ids", docs.stream().map(d -> d.get("id")).toList());
        payload.put("documents", docs.stream().map(d -> d.get("document")).toList());
        payload.put("metadatas", docs.stream().map(d -> d.get("metadata")).toList());

        try {
            String url = chromaUrl + "/api/v2/tenants/" + tenant + "/databases/" + databaseName + "/collections/" + collectionId + "/upsert";
            restTemplate.postForEntity(url, payload, Map.class);
            log.info("写入 Chroma 集合成功，collection={}, count={}", collectionName, docs.size());
        } catch (Exception e) {
            log.warn("写入 Chroma 集合失败，collection={}, error={}", collectionName, e.getMessage(), e);
        }
    }

    public List<String> query(String collectionName, String queryText, int limit) {
        ensureCollection(collectionName);
        String collectionId = findCollectionId(collectionName);
        if (collectionId == null || collectionId.isBlank()) {
            return List.of();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("query_texts", List.of(queryText));
        payload.put("n_results", limit);

        try {
            String url = chromaUrl + "/api/v2/tenants/" + tenant + "/databases/" + databaseName + "/collections/" + collectionId + "/query";
            Map response = restTemplate.postForObject(url, payload, Map.class);
            if (response == null) {
                return List.of();
            }

            Object resultObj = response.get("result");
            if (!(resultObj instanceof Map<?, ?> resultMap)) {
                return List.of();
            }

            Object documentsObj = resultMap.get("documents");
            if (!(documentsObj instanceof List<?> documentsList) || documentsList.isEmpty()) {
                return List.of();
            }

            Object firstDoc = documentsList.get(0);
            if (!(firstDoc instanceof List<?> docList)) {
                return List.of();
            }
            return docList.stream().map(String::valueOf).toList();
        } catch (Exception e) {
            log.warn("查询 Chroma 集合失败，collection={}, error={}", collectionName, e.getMessage(), e);
            return List.of();
        }
    }

    public void upsertQuestion(String collectionName, String id, String question, Map<String, Object> metadata) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", id);
        doc.put("document", question);
        doc.put("metadata", metadata);
        addDocuments(collectionName, List.of(doc));
    }

    public static void upsertQuestionStatic(String collectionName, String id, String question, Map<String, Object> metadata) {
        if (collectionName == null || collectionName.isBlank()) {
            return;
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", id);
        doc.put("document", question);
        doc.put("metadata", metadata);

        // 这里保留对外静态入口，避免再额外包装一层 QuestionRecommendationService。
        // 真实写入由实例方法 addDocuments 完成。
        ChromaService service = SpringContextHolder.getBean(ChromaService.class);
        service.addDocuments(collectionName, List.of(doc));
    }

    private String findCollectionId(String name) {
        try {
            Map<?, ?> response = restTemplate.getForObject(getCollectionsBaseUrl() + "?limit=100&offset=0", Map.class);
            if (response == null || !(response.get("collections") instanceof List<?> list)) {
                return null;
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && name.equals(String.valueOf(map.get("name")))) {
                    return String.valueOf(map.get("id"));
                }
            }
        } catch (Exception e) {
            log.warn("查找 Chroma collection id 失败，collection={}, error={}", name, e.getMessage());
        }
        return null;
    }

    private String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "default_database";
        }
        int index = jdbcUrl.toLowerCase().indexOf("databasename=");
        if (index < 0) {
            index = jdbcUrl.toLowerCase().indexOf("database=");
        }
        if (index < 0) {
            return "default_database";
        }

        String tail = jdbcUrl.substring(index + (jdbcUrl.toLowerCase().contains("databasename=") ? "databasename=".length() : "database=".length()));
        int end = tail.indexOf(';');
        if (end >= 0) {
            return tail.substring(0, end);
        }
        return tail;
    }
}
