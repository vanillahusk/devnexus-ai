package com.github.paicoding.forum.service.user.facade.impl;

import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;
import com.github.paicoding.forum.service.user.facade.AuthFacade;
import com.github.paicoding.forum.service.user.service.LoginService;
import com.github.paicoding.forum.service.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.service", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalAuthFacade implements AuthFacade {

    private final LoginService loginService;
    private final UserService userService;

    @Override
    public Long autoRegisterWxUserInfo(String uuid) {
        return loginService.autoRegisterWxUserInfo(uuid);
    }

    @Override
    public void logout(String session) {
        loginService.logout(session);
    }

    @Override
    public String loginByWx(Long userId) {
        return loginService.loginByWx(userId);
    }

    @Override
    public String loginByUserPwd(String username, String password) {
        return loginService.loginByUserPwd(username, password);
    }

    @Override
    public String registerByUserPwd(UserPwdLoginReq loginReq) {
        return loginService.registerByUserPwd(loginReq);
    }

    @Override
    public BaseUserInfoDTO resolveUserBySession(String session, String clientIp) {
        return userService.getAndUpdateUserIpInfoBySessionId(session, clientIp);
    }
}
