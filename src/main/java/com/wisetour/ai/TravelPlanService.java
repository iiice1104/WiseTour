package com.wisetour.ai;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;

/**
 * 行程规划 Agent 服务
 *
 * 负责初始化 LangChain4j Agent，注入 TravelPlanTools 工具集，
 * 并对外暴露 chat 方法供 Controller 调用。
 *
 * Agent 工作模式：ReAct（Reasoning + Acting）
 *   LLM 收到用户消息后，先推理需要调用哪些工具（Reasoning），
 *   框架自动执行工具并将结果回传给 LLM，LLM 再据此生成最终回答（Acting）。
 */
@Slf4j
@Service
public class TravelPlanService {

    @Value("${wisetour.ai.api-key}")
    private String apiKey;

    @Value("${wisetour.ai.base-url}")
    private String baseUrl;

    @Value("${wisetour.ai.model-name}")
    private String modelName;

    @Resource
    private TravelPlanTools travelPlanTools;

    private TravelPlanAgent agent;

    /**
     * 应用启动后初始化 Agent
     *
     * AiServices.builder() 是 LangChain4j 的核心 API：
     * - chatLanguageModel：指定底层 LLM（这里用 OpenAI 兼容接口，可接入 DeepSeek / 通义千问）
     * - tools：注入工具集，框架自动解析 @Tool 注解生成 Function Schema 传给 LLM
     * - chatMemoryProvider：按 sessionId 隔离对话历史，每个会话保留最近 10 条消息
     */
    @PostConstruct
    public void init() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();

        agent = AiServices.builder(TravelPlanAgent.class)
                .chatLanguageModel(model)
                .tools(travelPlanTools)
                // 每个 sessionId 独立维护对话记忆，保留最近 10 条消息（约 5 轮对话）
                .chatMemoryProvider(sessionId ->
                        MessageWindowChatMemory.withMaxMessages(10))
                .build();

        log.info("TravelPlanAgent 初始化完成，模型：{}，接入点：{}", modelName, baseUrl);
    }

    /**
     * 发起对话
     *
     * @param sessionId   会话ID，用于隔离多用户对话历史（可用用户ID）
     * @param userMessage 用户的自然语言输入
     * @return LLM 生成的行程规划回答
     */
    public String chat(String sessionId, String userMessage) {
        log.debug("用户[{}] 发起行程规划请求：{}", sessionId, userMessage);
        return agent.chat(sessionId, userMessage);
    }
}
