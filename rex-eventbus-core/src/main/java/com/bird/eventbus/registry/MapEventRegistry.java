package com.bird.eventbus.registry;

import cn.hutool.core.util.StrUtil;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MapEventRegistry implements IEventRegistry {
    private final static ConcurrentMap<String, Set<Method>> EVENT_HANDLER_CONTAINER = new ConcurrentHashMap<>();

    @Override
    public void add(String topic, Method method) {
        if (StrUtil.isBlank(topic) || method == null) {
            return;
        }
        Set<Method> eventHandlers = EVENT_HANDLER_CONTAINER.computeIfAbsent(topic, p -> new HashSet<>());
        eventHandlers.add(method);
        EVENT_HANDLER_CONTAINER.put(topic, eventHandlers);
    }

    @Override
    public Set<Method> getTopicMethods(String topic) {
        if (StrUtil.isBlank(topic)) {
            return new HashSet<>();
        }
        return EVENT_HANDLER_CONTAINER.getOrDefault(topic, new HashSet<>());
    }

    @Override
    public String[] getAllTopics() {
        Set<String> keys = EVENT_HANDLER_CONTAINER.keySet();
        return keys.toArray(new String[0]);
    }
}
