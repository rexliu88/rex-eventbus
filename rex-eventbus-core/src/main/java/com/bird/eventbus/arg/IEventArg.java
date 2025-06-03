package com.bird.eventbus.arg;

import java.io.Serializable;
import java.util.Date;

public interface IEventArg extends Serializable {
    String getEventId();
    Date getEventTime();
    default Boolean isLocal() {
        return false;
    }
    String getTraceId();
}
