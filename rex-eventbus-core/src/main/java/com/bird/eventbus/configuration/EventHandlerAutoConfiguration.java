package com.bird.eventbus.configuration;

import com.bird.eventbus.EventBus;
import com.bird.eventbus.EventbusConstant;
import com.bird.eventbus.handler.EventHandlerProperties;
import com.bird.eventbus.handler.EventMethodInitializer;
import com.bird.eventbus.registry.IEventRegistry;
import com.bird.eventbus.registry.MapEventRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = {EventbusConstant.Handler.GROUP})
@EnableConfigurationProperties(EventHandlerProperties.class)
public class EventHandlerAutoConfiguration {
    private final EventHandlerProperties handlerProperties;
    public EventHandlerAutoConfiguration(EventHandlerProperties handlerProperties) {
        this.handlerProperties = handlerProperties;
    }

    @Bean
    @ConditionalOnMissingBean(IEventRegistry.class)
    public IEventRegistry eventRegistry() {
        return new MapEventRegistry();
    }

    @Bean
    public EventMethodInitializer eventMethodInitializer(IEventRegistry eventRegistry) {
        EventMethodInitializer eventMethodInitializer = new EventMethodInitializer(this.handlerProperties, eventRegistry);
        eventMethodInitializer.initialize();
        return eventMethodInitializer;
    }

    @Bean
    public EventBus eventBus() {
        return new EventBus();
    }
}
