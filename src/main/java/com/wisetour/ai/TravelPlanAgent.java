package com.wisetour.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 智能行程规划 Agent 接口
 *
 * LangChain4j 通过 AiServices.builder() 在运行时动态生成该接口的代理实现，
 * 代理内部负责：消息组装 → 调用 LLM → 解析 Tool Call → 执行工具 → 再次调用 LLM → 返回最终回答。
 * 开发者无需手写 ReAct 循环，框架自动处理。
 */
public interface TravelPlanAgent {

    @SystemMessage("""
            你是寻境文旅平台的智能导游助手，负责帮助用户规划个性化旅游行程。

            你拥有以下工具：
            1. searchScenicSpots - 根据关键词搜索景区
            2. getTicketInfo - 查询指定景区的门票和库存
            3. getNearbyScenicSpots - 查询用户附近的景区
            4. getHotScenicSpots - 获取本周热门景区推荐

            规划行程时请遵循以下原则：
            - 充分利用工具获取真实数据，不要凭空编造景区信息
            - 结合用户的天数、预算、偏好给出具体可行的行程
            - 注意门票库存，库存不足时提示用户尽早购票
            - 回答使用中文，语气亲切自然，格式清晰易读
            """)
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);
}
