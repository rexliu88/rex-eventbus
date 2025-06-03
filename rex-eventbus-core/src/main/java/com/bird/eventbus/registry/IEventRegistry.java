package com.bird.eventbus.registry;

import com.bird.eventbus.handler.IHandler;
import java.util.Set;
public interface IEventRegistry {
    void register(Class<?> eventArgClass, IHandler handler);

    Set<IHandler> getEventArgHandlers(Class<?> eventArgClass);

    Class<?>[] getAllEventArgClass();
}
