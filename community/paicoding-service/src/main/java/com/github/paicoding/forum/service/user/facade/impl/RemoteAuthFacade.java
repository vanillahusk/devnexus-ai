package com.github.paicoding.forum.service.user.facade.impl;

import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.vo.login.UserNamePasswordReq;
import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;
import com.github.paicoding.forum.service.feign.FeignResultHelper;
import com.github.paicoding.forum.service.feign.auth.AuthInternalFeignClient;
import com.github.paicoding.forum.service.user.facade.AuthFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.service", name = "mode", havingValue = "remote")
public class RemoteAuthFacade implements AuthFacade {

    private final AuthInternalFeignClient authInternalFeignClient;

    @Override
    public Long autoRegisterWxUserInfo(String uuid) {
        return FeignResultHelper.unwrap(authInternalFeignClient.autoRegister(uuid), "远端认证服务自动注册失败");
    }

    @Override
    public void logout(String session) {
        FeignResultHelper.unwrap(authInternalFeignClient.logout(session), "远端认证服务登出失败");
    }

    @Override
    public String loginByWx(Long userId) {
        return FeignResultHelper.unwrap(authInternalFeignClient.wxLogin(userId), "远端认证服务微信登录失败");
    }

    @Override
    public String loginByUserPwd(String username, String password) {
        UserNamePasswordReq req = new UserNamePasswordReq();
        req.setUsername(username);
        req.setPassword(password);
        return FeignResultHelper.unwrap(authInternalFeignClient.login(req), "远端认证服务用户名密码登录失败");
    }

    @Override
    public String registerByUserPwd(UserPwdLoginReq loginReq) {
        String session = null;
        if (ReqInfoContext.getReqInfo() != null && ReqInfoContext.getReqInfo().getSession() != null) {
            session = ReqInfoContext.getReqInfo().getSession();
        }
        return FeignResultHelper.unwrap(authInternalFeignClient.register(loginReq, session), "远端认证服务注册失败");
    }

    @Override
    public BaseUserInfoDTO resolveUserBySession(String session, String clientIp) {
        return FeignResultHelper.unwrap(authInternalFeignClient.resolve(session, clientIp), "远端认证服务解析会话失败");
    }
}
