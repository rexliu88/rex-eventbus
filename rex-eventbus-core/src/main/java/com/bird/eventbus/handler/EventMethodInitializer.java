package com.bird.eventbus.handler;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import com.bird.eventbus.arg.IEventArg;
import com.bird.eventbus.registry.IEventRegistry;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class EventMethodInitializer {
    private boolean initialized = false;
    private final EventHandlerProperties handlerProperties;
    private final IEventRegistry eventRegistry;

    public EventMethodInitializer(EventHandlerProperties handlerProperties, IEventRegistry eventRegistry) {
        this.handlerProperties = handlerProperties;
        this.eventRegistry = eventRegistry;
    }

    public void initialize() {
        if (this.initialized) {
            return;
        }
        String scanPackages = handlerProperties.getScanPackages();
        if (StrUtil.isBlank(scanPackages)) {
            log.warn("事件处理方法扫码包路径不存在");
            return;
        }
        Set<Class<?>> classes = new HashSet<>();
        for (String scanPackage : scanPackages.split(",")) {
            log.info("扫描包路径：{}", scanPackage);
            classes.addAll(ClassUtil.scanPackage(scanPackage));
        }
        if (classes != null) {
            for (Class<?> clazz : classes) {
                for (Method method : clazz.getDeclaredMethods()) {
                    EventHandler eventAnnotation = method.getAnnotation(EventHandler.class);
                    if (eventAnnotation == null) {
                        continue;
                    }
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length != 1 || !IEventArg.class.isAssignableFrom(parameterTypes[0])) {
                        continue;
                    }
                    String argClassName = parameterTypes[0].getName();
                    this.eventRegistry.add(argClassName, method);
                }
            }
        }
        this.initialized = true;
    }
}
