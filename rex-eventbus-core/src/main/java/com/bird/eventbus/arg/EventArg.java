package com.bird.eventbus.arg;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Data
public class EventArg implements IEventArg {
    private String eventId;
    private Date eventTime;
    private String traceId;
    public EventArg(){
        eventId = UUID.randomUUID().toString();
        traceId = eventId;
        eventTime = new Date();
    }
    public void trace(String parentTraceId) {
        if (StrUtil.isNotBlank(parentTraceId)) {
            this.traceId = parentTraceId;
        }
    }

    public void trace(IEventArg parentEvent) {
        if (Objects.nonNull(parentEvent) && !parentEvent.getEventId().equals(this.eventId)) {
            this.traceId = parentEvent.getTraceId();
        }
    }
}
