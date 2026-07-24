package com.github.paicoding.forum.web.controller.ai.rest;

import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantHistoryItemDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReplyDTO;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.service.ai.facade.AiAssistantFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 社区 AI 知识助手
 *
 * @author Codex
 * @date 2026-04-01
 */
@RestController
@Permission(role = UserRole.LOGIN)
@RequestMapping(path = "ai/assistant/api")
public class AiAssistantRestController {

    private final AiAssistantFacade aiAssistantFacade;
    private final Executor aiStreamExecutor;

    public AiAssistantRestController(AiAssistantFacade aiAssistantFacade,
                                     @Qualifier("aiStreamExecutor") Executor aiStreamExecutor) {
        this.aiAssistantFacade = aiAssistantFacade;
        this.aiStreamExecutor = aiStreamExecutor;
    }

    @PostMapping(path = "ask")
    public ResVo<AiAssistantReplyDTO> ask(@RequestBody AiAssistantAskReq req) {
        return ResVo.ok(aiAssistantFacade.ask(req, ReqInfoContext.getReqInfo().getUserId()));
    }

    @PostMapping(path = "ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody AiAssistantAskReq req) {
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        Long userId = reqInfo.getUserId();
        SseEmitter emitter = new SseEmitter(60_000L);
        aiStreamExecutor.execute(() -> {
            ReqInfoContext.addReqInfo(reqInfo);
            try {
                streamReply(emitter, req, userId);
            } finally {
                ReqInfoContext.clear();
            }
        });
        return emitter;
    }

    private void streamReply(SseEmitter emitter, AiAssistantAskReq req, Long userId) {
        try {
            AiAssistantReplyDTO reply = aiAssistantFacade.ask(req, userId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sessionId", reply.getSessionId());
            metadata.put("route", reply.getRoute());
            metadata.put("degraded", reply.getDegraded());
            emitter.send(SseEmitter.event().name("metadata").data(metadata));
            String answer = reply.getAnswer() == null ? "" : reply.getAnswer();
            for (int offset = 0; offset < answer.length(); offset += 24) {
                emitter.send(SseEmitter.event().name("delta")
                        .data(answer.substring(offset, Math.min(answer.length(), offset + 24))));
            }
            emitter.send(SseEmitter.event().name("done").data(reply));
            emitter.complete();
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", e.getMessage())));
            } catch (IOException ignored) {
                // 客户端已经断开。
            }
            emitter.completeWithError(e);
        }
    }

    @GetMapping(path = "history")
    public ResVo<List<AiAssistantHistoryItemDTO>> history(@RequestParam("sessionId") String sessionId) {
        return ResVo.ok(aiAssistantFacade.history(sessionId, ReqInfoContext.getReqInfo().getUserId()));
    }
}
