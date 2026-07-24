package com.github.paicoding.forum.web.controller.auth.internal;

import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.login.UserNamePasswordReq;
import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;
import com.github.paicoding.forum.service.user.config.AuthServiceProperties;
import com.github.paicoding.forum.service.user.service.LoginService;
import com.github.paicoding.forum.service.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "internal/auth")
public class AuthInternalRestController {

    private final LoginService loginService;
    private final UserService userService;
    private final AuthServiceProperties properties;
    private final AuthInternalAccessValidator validator;

    @PostMapping(path = "password/login")
    public ResVo<String> login(@RequestBody UserNamePasswordReq req, HttpServletRequest request) {
        validate(request);
        return ResVo.ok(withReqInfo(() -> loginService.loginByUserPwd(req.getUsername(), req.getPassword())));
    }

    @PostMapping(path = "password/register")
    public ResVo<String> register(@RequestBody UserPwdLoginReq req,
                                  @RequestParam(value = "session", required = false) String session,
                                  HttpServletRequest request) {
        validate(request);
        return ResVo.ok(withReqInfo(session, () -> loginService.registerByUserPwd(req)));
    }

    @PostMapping(path = "wx/login")
    public ResVo<String> wxLogin(@RequestParam("userId") Long userId, HttpServletRequest request) {
        validate(request);
        return ResVo.ok(loginService.loginByWx(userId));
    }

    @PostMapping(path = "wx/autoRegister")
    public ResVo<Long> autoRegister(@RequestParam("uuid") String uuid, HttpServletRequest request) {
        validate(request);
        return ResVo.ok(withReqInfo(() -> loginService.autoRegisterWxUserInfo(uuid)));
    }

    @PostMapping(path = "logout")
    public ResVo<Boolean> logout(@RequestParam("session") String session, HttpServletRequest request) {
        validate(request);
        loginService.logout(session);
        return ResVo.ok(Boolean.TRUE);
    }

    @GetMapping(path = "session/resolve")
    public ResVo<BaseUserInfoDTO> resolve(@RequestParam("session") String session,
                                          @RequestParam(value = "clientIp", required = false) String clientIp,
                                          HttpServletRequest request) {
        validate(request);
        return ResVo.ok(userService.getAndUpdateUserIpInfoBySessionId(session, clientIp));
    }

    private void validate(HttpServletRequest request) {
        validator.validate(request.getHeader(properties.getTokenHeader()));
    }

    private <T> T withReqInfo(java.util.concurrent.Callable<T> callable) {
        return withReqInfo(null, callable);
    }

    private <T> T withReqInfo(String session, java.util.concurrent.Callable<T> callable) {
        ReqInfoContext.ReqInfo old = ReqInfoContext.getReqInfo();
        ReqInfoContext.ReqInfo reqInfo = new ReqInfoContext.ReqInfo();
        if (session != null) {
            reqInfo.setSession(session);
            BaseUserInfoDTO user = userService.getAndUpdateUserIpInfoBySessionId(session, null);
            if (user != null) {
                reqInfo.setUserId(user.getUserId());
                reqInfo.setUser(user);
            }
        }
        ReqInfoContext.addReqInfo(reqInfo);
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (old != null) {
                ReqInfoContext.addReqInfo(old);
            } else {
                ReqInfoContext.clear();
            }
        }
    }
}
