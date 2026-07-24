package com.github.paicoding.forum.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
public class RuntimeConfigProbe {

    @Value("${paicoding.runtime.probe:local-default}")
    private String value;

    public String value() {
        return value;
    }
}
