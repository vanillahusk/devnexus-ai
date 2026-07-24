package com.github.paicoding.forum.service.notify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "message.service")
public class MessageServiceProperties {

    /**
     * 通知服务调用模式：local / remote
     */
    private String mode = "local";

    /**
     * 远端消息服务地址
     */
    private String baseUrl = "http://localhost:8095";

    /**
     * 消息服务 serviceId
     */
    private String serviceId = "message-service";

    /**
     * 内部读接口前缀
     */
    private String readInternalPath = "/internal/message/notify";

    /**
     * 内部写接口前缀
     */
    private String commandInternalPath = "/internal/message/notify/command";

    /**
     * 服务间调用 token 请求头
     */
    private String tokenHeader = "X-MESSAGE-INTERNAL-TOKEN";

    /**
     * 透传 userId 的请求头
     */
    private String userIdHeader = "X-MESSAGE-USER-ID";

    /**
     * 服务间调用 token
     */
    private String token;
}
