package com.github.paicoding.forum.service.feign.auth;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.login.UserNamePasswordReq;
import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "${auth.service.service-id:${auth.service.serviceId:auth-service}}",
        contextId = "authInternalFeignClient",
        path = "${auth.service.internal-path:${auth.service.internalPath:/internal/auth}}",
        configuration = AuthFeignConfiguration.class,
        fallbackFactory = AuthInternalFallbackFactory.class
)
public interface AuthInternalFeignClient {

    @PostMapping("password/login")
    ResVo<String> login(@RequestBody UserNamePasswordReq req);

    @PostMapping("password/register")
    ResVo<String> register(@RequestBody UserPwdLoginReq req,
                           @RequestParam(value = "session", required = false) String session);

    @PostMapping("wx/login")
    ResVo<String> wxLogin(@RequestParam("userId") Long userId);

    @PostMapping("wx/autoRegister")
    ResVo<Long> autoRegister(@RequestParam("uuid") String uuid);

    @PostMapping("logout")
    ResVo<Boolean> logout(@RequestParam("session") String session);

    @GetMapping("session/resolve")
    ResVo<BaseUserInfoDTO> resolve(@RequestParam("session") String session,
                                   @RequestParam(value = "clientIp", required = false) String clientIp);
}
