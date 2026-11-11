package org.sqlagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.sqlagent.util.SqlCleaner;
import org.sqlagent.util.SqlValidator;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OllamaAiService {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    SchemaService schemaService;

    @Autowired
    PromptService promptService;

    @Autowired
    SqlService sqlService;

    public String generateSql(String question) {
        return generateSql(question, null, null);
    }

    public String generateSql(String question, String lastSql, String lastError) {
        String prompt = promptService.loadPrompt("sql-generation.txt");
        String retryContext = (lastSql == null || lastError == null)
                ? ""
                : "Previous SQL:\n" + lastSql + "\n\nPrevious error:\n" + lastError;

        prompt = prompt.replace("{{schema}}", schemaService.getSchema().toString())
                .replace("{{question}}", question)
                .replace("{{retry_context}}", retryContext);

        log.info("收到请求: {}", question);

        try {
            return SqlCleaner.clean(callOllama(prompt));
        } catch (Exception e) {
            log.error("生成 SQL 失败，question={}", question, e);
            throw e;
        }
    }

    public Map<String, Object> queryWithRetry(String question) {
        String sql = null;
        String lastError = null;

        for (int i = 0; i < 3; i++) {
            sql = generateSql(question, sql, lastError);
            try {
                SqlValidator.validate(sql);
                List<Map<String, Object>> data = sqlService.execute(sql);
                Map<String, Object> result = new HashMap<>();
                result.put("sql", sql);
                result.put("data", data);
                return result;
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("SQL 执行失败，重试第 {} 次，question={}，sql={}，error={}", i + 1, question, sql, lastError);
            }
        }

        throw new RuntimeException("SQL 生成与执行失败，已重试 3 次，最后错误: " + lastError);
    }

    private String callOllama(String prompt) {

        Map<String, Object> request =
                new HashMap<>();

        request.put("model", "qwen2.5:3b");
        request.put("prompt", prompt);
        request.put("stream", false);

        try {
            Map response =
                    restTemplate.postForObject(
                            "http://localhost:11434/api/generate",
                            request,
                            Map.class
                    );

            log.info("Ollama 原始返回: {}",  response.get("response"));

            return String.valueOf(
                    response.get("response")
            );
        } catch (Exception e) {
            log.error("调用 Ollama 失败，prompt={}", prompt, e);
            throw e;
        }
    }

    public String generateAnswer(
            String question,
            Object result) throws IOException {
        String prompt =
                promptService
                        .loadPrompt(
                                "answer-generation.txt");

        prompt = prompt
                .replace(
                        "{{question}}",
                        question
                )
                .replace(
                        "{{result}}",
                        result.toString()
                );

        try {
            Map<String, Object> request =
                    new HashMap<>();

            request.put("model", "qwen2.5:3b");
            request.put("prompt", prompt);
            request.put("stream", false);

            Map response =
                    restTemplate.postForObject(
                            "http://localhost:11434/api/generate",
                            request,
                            Map.class
                    );

            return String.valueOf(
                    response.get("response")
            );
        } catch (Exception e) {
            log.error("生成回答失败，question={}, result={}", question, result, e);
            throw e;
        }
    }

    @Cacheable(value = "example-questions", key = "'default'")
    public List<String> generateExamples() {
        return generateExamples(false);
    }

    public List<String> generateExamples(boolean forceRefresh) {
        String schema = schemaService.getSchema();

        String prompt = promptService.loadPrompt("example-questions.txt");
        prompt = prompt.replace("{{schema}}", schema);

        try {
            String result = callOllama(prompt);
            return Arrays.stream(result.split("\n"))
                    .filter(s -> !s.isBlank())
                    .map(String::trim)
                    .toList();
        } catch (Exception e) {
            log.error("生成推荐问题失败，schema={}", schema, e);
            throw e;
        }
    }

    @CacheEvict(value = "example-questions", key = "'default'")
    public void forceRefresh() {
        log.info("已清空推荐问题缓存");
    }
}