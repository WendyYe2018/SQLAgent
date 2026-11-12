package org.sqlagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class QdrantService {

    private final RestTemplate restTemplate = new RestTemplate();

    public void saveUserQueryToVector(String question) {
        try {
            String collectionName = "user_queries";
            ensureQdrantCollection(collectionName);

            Map<String, Object> payload = new HashMap<>();
            payload.put("question", question);
            payload.put("source", "user_query");
            payload.put("created_at", Instant.now().toString());

            // todo what is point?
            Map<String, Object> point = new HashMap<>();
            point.put("id", UUID.randomUUID().toString());
            point.put("vector", buildQuestionVector(question));
            point.put("payload", payload);

            Map<String, Object> body = new HashMap<>();
            body.put("points", List.of(point));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.put("http://localhost:6333/collections/" + collectionName + "/points?wait=true", request, Map.class);
        } catch (Exception e) {
            // 这里不阻断用户查询结果，保证主业务可用。
            log.error("存储向量数据库失败", e);
        }
    }

    private void ensureQdrantCollection(String collectionName) {
        try {
            Map<String, Object> body = new HashMap<>();
            Map<String, Object> vectors = new HashMap<>();
            vectors.put("size", 3);
            vectors.put("distance", "Cosine");
            body.put("vectors", vectors);
            restTemplate.put("http://localhost:6333/collections/" + collectionName, body);
        } catch (Exception e) {
            log.error("确保 Qdrant 集合失败", e);
            // collection already exists or qdrant is unavailable
        }
    }

    private List<Double> buildQuestionVector(String question) {
        if (question == null || question.isBlank()) {
            return List.of(0.0, 0.0, 0.0);
        }

        byte[] bytes = question.getBytes(StandardCharsets.UTF_8);
        long hash = 0L;
        for (byte b : bytes) {
            hash = (hash * 131L) + (b & 0xFFL);
        }

        double x = ((hash & 0xFFL) / 255.0) * 2.0 - 1.0;
        double y = (((hash >> 8) & 0xFFL) / 255.0) * 2.0 - 1.0;
        double z = (((hash >> 16) & 0xFFL) / 255.0) * 2.0 - 1.0;
        return List.of(x, y, z);
    }

}
