package com.bird.eventbus.registry;

import cn.hutool.core.collection.CollectionUtil;
import com.bird.eventbus.arg.IEventArg;
import com.bird.eventbus.handler.AbstractHandler;
import com.bird.eventbus.handler.EventHandler;
import com.bird.eventbus.handler.IHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class MapEventRegistry implements IEventRegistry, InitializingBean, DisposableBean {
    private final static ConcurrentMap<Class<?>, Set<IHandler>> EVENT_HANDLER_CONTAINER = new ConcurrentHashMap<>();

    @Override
    public void register(Class<?> eventArgClass, IHandler handler) {
        String eventArgClassName = eventArgClass.getName();
        log.info("register eventArg class:{}", eventArgClassName);
        Set<IHandler> eventHandlers = EVENT_HANDLER_CONTAINER.computeIfAbsent(eventArgClass, p -> new HashSet<>());
        eventHandlers.add(handler);
        EVENT_HANDLER_CONTAINER.put(eventArgClass, eventHandlers);
    }

    @Override
    public Set<IHandler> getEventArgHandlers(Class<?> eventArgClass) {
        if (eventArgClass == null || !IEventArg.class.isAssignableFrom(eventArgClass)) {
            return new HashSet<>();
        }
        return EVENT_HANDLER_CONTAINER.getOrDefault(eventArgClass, new HashSet<>());
    }

    @Override
    public Class<?>[] getAllEventArgClass() {
        Set<Class<?>> keys = EVENT_HANDLER_CONTAINER.keySet();
        return keys.toArray(new Class<?>[0]);
    }

    public int getAllEventArgClassCount() {
        return EVENT_HANDLER_CONTAINER.size();
    }

    @Override
    public void destroy() throws Exception {
        EVENT_HANDLER_CONTAINER.clear();
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, AbstractHandler> beans = this.applicationContext.getBeansOfType(AbstractHandler.class);
        if (CollectionUtil.isNotEmpty(beans)) {
            for (AbstractHandler handler : beans.values()) {
                Class<?> clazz = handler.getClass();
                try {
                    Method onEventMethod = clazz.getMethod("onEvent", AbstractHandler.class);
                    Class<?>[] parameterTypes = onEventMethod.getParameterTypes();
                    if (parameterTypes.length != 1 || !IEventArg.class.isAssignableFrom(parameterTypes[0])) {
                        continue;
                    }
                    Class<?> eventArgClass11 = parameterTypes[0];
                    log.info("eventArgClass2 class:{},name:{}", eventArgClass11, eventArgClass11.getName());
                    Class<?> eventArgClass22 = (Class<?>)
                            (
                                    (ParameterizedType)
                                            parameterTypes[0].getGenericSuperclass()
                            ).getActualTypeArguments()[0];
                    log.info("eventArgClass2 class:{},name:{}", eventArgClass22, eventArgClass22.getName());
                    // register(eventArgClass, handler);
                } catch (Exception e) {
                    log.error("register subscribe error class:{},exception:{}", clazz, e);
                }
                for (Method method : clazz.getDeclaredMethods()) {
                    EventHandler eventAnnotation = method.getAnnotation(EventHandler.class);
                    if (eventAnnotation == null) {
                        continue;
                    }
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length != 1 || !IEventArg.class.isAssignableFrom(parameterTypes[0])) {
                        continue;
                    }
                    Class<?> eventArgClass = parameterTypes[0];
                    register(eventArgClass, handler);

                    if("onEvent".equals(method.getName())) {
                        log.info("register subscribe class:{},method:{}", clazz, method.getName());
                        Class<?>[] parameterTypes2 = method.getParameterTypes();
                        if (parameterTypes2.length != 1 || !IEventArg.class.isAssignableFrom(parameterTypes2[0])) {
                            continue;
                        }
                        Class<?> eventArgClass2 = parameterTypes[0];
                        register(eventArgClass2, handler);
                        log.info("eventArgClass2 class:{},name:{}", eventArgClass2, eventArgClass2.getName());
                    }
                }
            }
        }
    }
}
