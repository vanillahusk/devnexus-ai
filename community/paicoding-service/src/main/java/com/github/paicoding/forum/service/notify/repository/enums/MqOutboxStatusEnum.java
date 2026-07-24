package com.github.paicoding.forum.service.notify.repository.enums;

import lombok.Getter;

@Getter
public enum MqOutboxStatusEnum {
    PENDING(0),
    SENDING(1),
    RETRY(2),
    SENT(3),
    DEAD(4);

    private final int code;

    MqOutboxStatusEnum(int code) {
        this.code = code;
    }
}
