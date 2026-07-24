package com.github.paicoding.forum.web.admin.rest;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.service.ai.service.AiExternalCallGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("api/admin/ai/governance")
@Permission(role = UserRole.ADMIN)
public class AiGovernanceAdminRestController {

    private final AiExternalCallGuard externalCallGuard;

    public AiGovernanceAdminRestController(AiExternalCallGuard externalCallGuard) {
        this.externalCallGuard = externalCallGuard;
    }

    @GetMapping("circuits")
    public ResVo<Map<String, AiExternalCallGuard.CircuitSnapshot>> circuits() {
        return ResVo.ok(Map.of(
                "ragent", externalCallGuard.snapshot("ragent"),
                "api", externalCallGuard.snapshot("api")
        ));
    }
}
