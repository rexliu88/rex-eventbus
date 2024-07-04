package com.bird.eventbus.handler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@Data
@ConfigurationProperties(value = "bird.eventbus.handler")
public class EventHandlerProperties {
    private String group;
    private String scanPackages;
}
