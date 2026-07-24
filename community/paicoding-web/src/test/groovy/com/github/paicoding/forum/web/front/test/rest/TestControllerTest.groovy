package com.github.paicoding.forum.web.front.test.rest

import com.github.paicoding.forum.api.model.context.ReqInfoContext
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO
import com.github.paicoding.forum.web.controller.test.rest.TestController
import com.github.paicoding.forum.web.hook.interceptor.GlobalViewInterceptor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

@SuppressWarnings("all")
class TestControllerTest extends Specification {

    def "test email"() {
        given: "prepare beans"
        def baseUserInfo = new BaseUserInfoDTO()
        baseUserInfo.setRole(role)
        def reqInfo = new ReqInfoContext.ReqInfo()
        reqInfo.setUserId(111L)
        reqInfo.setUser(baseUserInfo)
        if (role != null) {
            ReqInfoContext.addReqInfo(reqInfo)
        } else {
            ReqInfoContext.clear()
        }
        def controller = new TestController() {
            @Override
            protected boolean sendMail(String title, String to, String content) {
                return true
            }
        }
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addInterceptors(new GlobalViewInterceptor())
                .build()
        when: "execute email"
        def result = mockMvc.perform(MockMvcRequestBuilders
                .get("/test/email")
                .param("to", "admin@test.com"))
                .andExpect { it.getResponse().getStatus() == status }
                .andReturn()
                .getResponse()
                .getContentAsString()
        then: "verify result"
        result.contains(keyText)
        where: "param role and result"
        role     | keyText  | status
        "ADMIN"  | "true"   | 200
        "NORMAL" | ""       | 403
        null     | "未登录" | 200
    }

}
