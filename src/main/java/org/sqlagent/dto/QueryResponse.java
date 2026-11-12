package org.sqlagent.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 查询响应对象。
 * 包含 SQL 执行结果、自然语言回答以及失败/澄清相关信息，供前端统一展示。
 */
public class QueryResponse {

    /**
     * 自然语言回答结果，通常为对查询结果的中文说明。
     */
    @Setter
    @Getter
    private String answer;

    /**
     * 当前执行成功/最终生成的 SQL 语句。
     */
    @Setter
    @Getter
    private String sql;

    /**
     * SQL 查询返回的数据集合，每一项为一行记录，字段名为列名。
     */
    @Setter
    @Getter
    private List<Map<String, Object>> data;

    /**
     * 错误信息，适用于查询失败、SQL 执行异常等场景。
     */
    @Setter
    @Getter
    private String error;

    /**
     * 响应状态，例如 success、error、needs_clarification。
     */
    @Setter
    @Getter
    private String status;

    /**
     * 需要用户澄清时，返回的澄清问题。
     */
    @Setter
    @Getter
    private String clarificationQuestion;

    /**
     * 最后一次尝试生成/执行的 SQL，用于前端回显和错误定位。
     */
    @Setter
    @Getter
    private String lastAttemptSql;

    /**
     * 最后一次失败的错误信息，用于展示重试失败原因。
     */
    @Setter
    @Getter
    private String lastAttemptError;

}