package com.bird.eventbus.registry;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
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
    private final static ConcurrentMap<Class<?>, Set<AbstractHandler>> EVENT_HANDLER_CONTAINER = new ConcurrentHashMap<>();

    @Override
    public void register(Class<?> eventArgClass, AbstractHandler handler) {
        String eventArgClassName = eventArgClass.getName();
        log.info("register eventArg class:{}", eventArgClassName);
        Set<AbstractHandler> eventHandlers = EVENT_HANDLER_CONTAINER.computeIfAbsent(eventArgClass, p -> new HashSet<>());
        eventHandlers.add(handler);
        EVENT_HANDLER_CONTAINER.put(eventArgClass, eventHandlers);
    }

    @Override
    public Set<AbstractHandler> getEventArgHandlers(Class<?> eventArgClass) {
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
                log.info("clazz class:{},name:{}", clazz, clazz.getName());
                for (Method method : clazz.getDeclaredMethods()) {
                    log.info("eventArgClass00 method:{},name:{},parameter:{}", method, method.getName(), JSONUtil.toJsonStr(method.getParameterTypes()));
                    EventHandler eventAnnotation = method.getAnnotation(EventHandler.class);
                    if (eventAnnotation != null) {
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        if (parameterTypes.length != 1 || !IEventArg.class.isAssignableFrom(parameterTypes[0])) {
                            continue;
                        }
                        Class<?> eventArgClass00 = parameterTypes[0];
                        if(eventArgClass00 != null && IEventArg.class.isAssignableFrom(eventArgClass00)) {
                            register(eventArgClass00, handler);
                        }
                        log.info("eventArgClass00 class:{},name:{}", eventArgClass00, eventArgClass00.getName());
                    }
                    if ("HandleEvent".equals(method.getName())) {
                        log.info("register subscribe class:{},method:{}", clazz, method.getName());
                        Class<?>[] parameterTypes2 = method.getParameterTypes();
                        if (parameterTypes2.length != 1 || !IEventArg.class.isAssignableFrom(parameterTypes2[0])) {
                            continue;
                        }
                        Class<?> eventArgClass11 = parameterTypes2[0];
                        if(eventArgClass11 != null && IEventArg.class.isAssignableFrom(eventArgClass11)) {
                            register(eventArgClass11, handler);
                        }
                        log.info("eventArgClass11 class:{},name:{}", eventArgClass11, eventArgClass11.getName());
                    }
                    if ("onEvent".equals(method.getName())) {
                        log.info("register subscribe class:{},method:{}", clazz, method.getName());
                        Class<?>[] parameterTypes3 = method.getParameterTypes();
                        if (parameterTypes3.length != 1 || !IEventArg.class.isAssignableFrom(parameterTypes3[0])) {
                            continue;
                        }
                        Class<?> eventArgClass22 = parameterTypes3[0];
                        if(eventArgClass22 != null && IEventArg.class.isAssignableFrom(eventArgClass22)) {
                            register(eventArgClass22, handler);
                        }
                        log.info("eventArgClass22 class:{},name:{}", eventArgClass22, eventArgClass22.getName());
                    }
                }
            }
        }
    }
}
