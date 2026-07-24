package com.github.paicoding.forum.service.feign.auth;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.api.model.vo.login.UserNamePasswordReq;
import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthInternalFallbackFactory implements FallbackFactory<AuthInternalFeignClient> {

    @Override
    public AuthInternalFeignClient create(Throwable cause) {
        return new AuthInternalFeignClient() {
            private <T> ResVo<T> unavailable() {
                return ResVo.fail(StatusEnum.UNEXPECT_ERROR, "认证服务暂时不可用");
            }

            @Override public ResVo<String> login(UserNamePasswordReq req) { return unavailable(); }
            @Override public ResVo<String> register(UserPwdLoginReq req, String session) { return unavailable(); }
            @Override public ResVo<String> wxLogin(Long userId) { return unavailable(); }
            @Override public ResVo<Long> autoRegister(String uuid) { return unavailable(); }
            @Override public ResVo<Boolean> logout(String session) { return unavailable(); }
            @Override public ResVo<BaseUserInfoDTO> resolve(String session, String clientIp) { return unavailable(); }
        };
    }
}
