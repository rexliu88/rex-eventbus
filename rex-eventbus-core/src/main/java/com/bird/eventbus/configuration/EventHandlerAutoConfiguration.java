package com.bird.eventbus.configuration;

import com.bird.eventbus.EventBus;
import com.bird.eventbus.registry.IEventRegistry;
import com.bird.eventbus.registry.MapEventRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EventHandlerAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(IEventRegistry.class)
    public IEventRegistry eventRegistry() {
        return new MapEventRegistry();
    }

    @Bean
    public EventBus eventBus() {
        return new EventBus();
    }
}
