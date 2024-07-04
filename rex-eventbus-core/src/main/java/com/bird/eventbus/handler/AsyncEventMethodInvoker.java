package com.bird.eventbus.handler;

import cn.hutool.extra.spring.SpringUtil;
import com.bird.eventbus.arg.IEventArg;
import com.bird.eventbus.registry.IEventRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.Callable;

@Data
@Slf4j
public class AsyncEventMethodInvoker implements Callable<EventHandleStatusEnum> {
    IEventArg eventArg;
    @Override
    public EventHandleStatusEnum call() throws Exception {
        IEventRegistry eventRegistry = SpringUtil.getBean(IEventRegistry.class);
        String eventKey = eventArg.getClass().getName();
        Set<Method> methods = eventRegistry.getTopicMethods(eventKey);
        if (!CollectionUtils.isEmpty(methods)) {
            int successCount = 0;
            for (Method method : methods) {
                Class<?> typeClass = method.getDeclaringClass();
                try {
                    Object instance = SpringUtil.getBean(typeClass);
                    method.invoke(instance, eventArg);
                } catch (InvocationTargetException e) {
                    log.error("事件消费失败1-AsyncEventMethodInvoker", e);
                    continue;
                } catch (Exception e) {
                    log.error("事件消费失败2-AsyncEventMethodInvoker", e);
                    continue;
                }
                successCount++;
            }
            if (successCount >= methods.size()) {
                log.info("事件调用成功-AsyncEventMethodInvoker", eventArg);
                return EventHandleStatusEnum.SUCCESS;
            } else if (successCount > 0) {
                log.error("事件调用部分成功-AsyncEventMethodInvoker", eventArg);
                return EventHandleStatusEnum.PARTIAL_SUCCESS;
            } else {
                log.error("事件调用失败-AsyncEventMethodInvoker", eventArg);
                return EventHandleStatusEnum.FAIL;
            }
        }
        return EventHandleStatusEnum.DEADEVENT;
    }
}
