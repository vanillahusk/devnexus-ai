package com.github.paicoding.forum.web.controller.ai.rest;

import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.ai.AiAgentAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAgentReplyDTO;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.service.ai.service.AiAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区受控 Agent 门面。Ragent 凭据只存在于服务端。
 */
@RestController
@RequiredArgsConstructor
@Permission(role = UserRole.LOGIN)
@RequestMapping(path = "ai/agent/api")
public class AiAgentRestController {
    private final AiAgentService aiAgentService;

    @PostMapping(path = "query")
    public ResVo<AiAgentReplyDTO> query(@RequestBody AiAgentAskReq request) {
        return ResVo.ok(aiAgentService.query(request, ReqInfoContext.getReqInfo().getUserId()));
    }
}
