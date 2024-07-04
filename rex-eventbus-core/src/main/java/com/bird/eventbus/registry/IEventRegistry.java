package com.bird.eventbus.registry;

import java.lang.reflect.Method;
import java.util.Set;
public interface IEventRegistry {
    void add(String topic, Method method);
    Set<Method> getTopicMethods(String topic);
    String[] getAllTopics();
}
