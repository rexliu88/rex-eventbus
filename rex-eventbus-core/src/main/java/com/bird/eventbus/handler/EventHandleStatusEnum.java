package com.bird.eventbus.handler;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum  EventHandleStatusEnum {
    SUCCESS("SUCCESS", "成功"),
    PARTIAL_SUCCESS("PARTIAL_SUCCESS", "部分成功"),
    FAIL("FAIL", "失败"),
    TIMEOUT("TIMEOUT", "超时"),
    DEADEVENT("DEADEVENT", "死信");

    private final String code;
    private final String name;
}

