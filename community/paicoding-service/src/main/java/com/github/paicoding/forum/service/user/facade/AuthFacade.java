package com.github.paicoding.forum.service.user.facade;

import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;

public interface AuthFacade {

    Long autoRegisterWxUserInfo(String uuid);

    void logout(String session);

    String loginByWx(Long userId);

    String loginByUserPwd(String username, String password);

    String registerByUserPwd(UserPwdLoginReq loginReq);

    BaseUserInfoDTO resolveUserBySession(String session, String clientIp);
}
