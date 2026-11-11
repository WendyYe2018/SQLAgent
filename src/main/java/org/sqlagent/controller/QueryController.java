package org.sqlagent.controller;

import org.springframework.web.bind.annotation.*;
import org.sqlagent.dto.QueryRequest;
import org.sqlagent.dto.QueryResponse;
import org.sqlagent.service.MockAiService;
import org.sqlagent.service.OllamaAiService;
import org.sqlagent.service.SqlService;
import org.sqlagent.util.SqlValidator;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final MockAiService mockAiService;
    private final SqlService sqlService;
    private final OllamaAiService ollamaAiService;

    public QueryController(
            MockAiService mockAiService,
            SqlService sqlService, OllamaAiService ollamaAiService) {

        this.mockAiService = mockAiService;
        this.sqlService = sqlService;
        this.ollamaAiService = ollamaAiService;
    }


    @GetMapping("/examples")
    public List<String> examples(@RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        if (refresh) {
            ollamaAiService.forceRefresh();
            return ollamaAiService.generateExamples();
        }
        return ollamaAiService.generateExamples();
    }

    @PostMapping("/query")
    public QueryResponse query(
            @RequestBody QueryRequest request) {

        QueryResponse response =
                new QueryResponse();
        try {
            String question = request.getQuestion();
            Map<String, Object> queryResult = ollamaAiService.queryWithRetry(question);
            String sql = (String) queryResult.get("sql");
            List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");

            response.setSql(sql);
            response.setData(data);

            String answer =
                    ollamaAiService.generateAnswer(
                            question,
                            data
                    );

            response.setAnswer(answer);

        } catch (Exception e) {
            response.setAnswer("查询失败");
            response.setError(e.getMessage());
            e.printStackTrace();
        }

        return response;
    }
}