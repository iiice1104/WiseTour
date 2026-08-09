package com.wisetour.dto;

import lombok.Data;

/**
 * 行程规划请求 DTO
 */
@Data
public class TravelPlanRequest {

    /**
     * 会话ID：用于隔离不同用户的对话历史。
     * 前端可用用户ID或随机UUID，同一 sessionId 的连续对话共享上下文。
     */
    private String sessionId;

    /**
     * 用户输入的自然语言消息。
     * 示例："我有3天时间在北京，预算500元，喜欢历史文化，帮我规划行程"
     */
    private String message;
}
