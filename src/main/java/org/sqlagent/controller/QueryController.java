package org.sqlagent.controller;

import org.springframework.web.bind.annotation.*;
import org.sqlagent.dto.QueryRequest;
import org.sqlagent.dto.QueryResponse;
import org.sqlagent.service.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final SqlService sqlService;
    private final OllamaAiService ollamaAiService;
    private final AgentExecutionLogService agentExecutionLogService;

    public QueryController(
            SqlService sqlService,
            OllamaAiService ollamaAiService,
            AgentExecutionLogService agentExecutionLogService) {

        this.sqlService = sqlService;
        this.ollamaAiService = ollamaAiService;
        this.agentExecutionLogService = agentExecutionLogService;
    }


    @GetMapping("/examples")
    public List<String> examples(@RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        if (refresh) {
            ollamaAiService.forceRefresh();
            return ollamaAiService.generateExamples();
        }

        List<String> examples = ollamaAiService.generateExamples();
        if (examples == null || examples.isEmpty()) {
            return List.of();
        }
        try {
        // 先生成 examples，然后写入 Chroma 向量库，确保前端展示和向量检索都使用同一批数据。
            String collectionName = "taobao_questions";
            if (examples != null && !examples.isEmpty()) {
                for (int i = 0; i < examples.size(); i++) {
                    String example = examples.get(i);
                    ChromaService.upsertQuestionStatic(collectionName, String.valueOf(i + 1), example, Map.of("source", "generated_examples"));
                }
            }
        } catch (Exception e) {

        }
        return examples;
    }

    @PostMapping("/query")
    public QueryResponse query(
            @RequestBody QueryRequest request) {

        QueryResponse response =
                new QueryResponse();
        try {
            String question = request.getQuestion();
            Map<String, Object> queryResult = ollamaAiService.queryWithRetry(question);
            String sql = (String) queryResult.getOrDefault("sql", "");
            List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");
            response.setStatus((String) queryResult.getOrDefault("status", "success"));
            response.setSql(sql);
            response.setData(data);
            response.setClarificationQuestion((String) queryResult.get("clarificationQuestion"));
            response.setLastAttemptSql((String) queryResult.get("lastAttemptSql"));
            response.setLastAttemptError((String) queryResult.get("lastAttemptError"));

            if ("needs_clarification".equals(response.getStatus())) {
                response.setAnswer(response.getClarificationQuestion());
                response.setSql(response.getLastAttemptSql() != null ? response.getLastAttemptSql() : sql);
                return response;
            }

            String answer =
                    ollamaAiService.generateAnswer(
                            question,
                            data
                    );

            response.setAnswer(answer);

        } catch (Exception e) {
            response.setAnswer("查询失败");
            response.setError(e.getMessage());
            response.setStatus("error");
            e.printStackTrace();
        }

        return response;
    }

    @GetMapping("/agent-logs")
    public List<Map<String, Object>> agentLogs() {
        return agentExecutionLogService.getLogs();
    }
}