package com.bird.eventbus.registry;

import com.bird.eventbus.handler.AbstractHandler;
import com.bird.eventbus.handler.IHandler;
import java.util.Set;
public interface IEventRegistry {
    void register(Class<?> eventArgClass, AbstractHandler handler);

    Set<AbstractHandler> getEventArgHandlers(Class<?> eventArgClass);

    Class<?>[] getAllEventArgClass();
}
