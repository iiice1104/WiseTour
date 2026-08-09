package com.wisetour.controller;

import com.wisetour.ai.TravelPlanService;
import com.wisetour.dto.Result;
import com.wisetour.dto.TravelPlanRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 智能行程规划接口
 *
 * POST /travel/plan
 * Body: { "sessionId": "user_123", "message": "我有3天在北京，帮我规划行程" }
 */
@Slf4j
@RestController
@RequestMapping("/travel")
public class TravelPlanController {

    @Resource
    private TravelPlanService travelPlanService;

    /**
     * 智能行程规划对话接口
     *
     * 支持多轮对话：相同 sessionId 的请求共享上下文，
     * 用户可以追问"第二天改去哪里""帮我查一下故宫的票还有多少"等。
     *
     * @param request 包含 sessionId 和用户消息
     * @return Agent 生成的行程方案或追问回答
     */
    @PostMapping("/plan")
    public Result plan(@RequestBody TravelPlanRequest request) {
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            return Result.fail("sessionId 不能为空");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Result.fail("消息内容不能为空");
        }

        String answer = travelPlanService.chat(request.getSessionId(), request.getMessage());
        return Result.ok(answer);
    }
}
