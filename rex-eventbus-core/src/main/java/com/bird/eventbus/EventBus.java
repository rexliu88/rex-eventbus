package com.bird.eventbus;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.bird.eventbus.arg.IEventArg;
import com.bird.eventbus.handler.EventHandleStatusEnum;
import com.bird.eventbus.handler.IHandler;
import com.bird.eventbus.registry.IEventRegistry;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@NoArgsConstructor
public class EventBus {
    public static void push(IEventArg eventArg) {
        log.info("事件发送开始。");
        FutureTask<EventHandleStatusEnum> task = asyncPush(eventArg);
        EventHandleStatusEnum eventHandleStatusEnum = EventHandleStatusEnum.FAIL;
        if (task == null) {
            log.error("事件处理失败:执行任务为空");
            return;
        }
        try {
            eventHandleStatusEnum = task.get();
        } catch (InterruptedException e) {
            log.error("事件处理失败", e);
        } catch (ExecutionException e) {
            log.error("事件消费失败1", e);
        }
        if (eventHandleStatusEnum == EventHandleStatusEnum.FAIL) {
            log.error("事件处理失败");
        }
        if (eventHandleStatusEnum == EventHandleStatusEnum.DEADEVENT) {
            log.error("事件处理失败:死信，无处理器");
        }
        if (eventHandleStatusEnum == EventHandleStatusEnum.PARTIAL_SUCCESS) {
            log.error("事件处理失败:部分成功");
        }
        if (eventHandleStatusEnum == EventHandleStatusEnum.TIMEOUT) {
            log.error("事件处理失败:超时");//目前无
        }
        log.info("事件发送结束！");
    }

    public static FutureTask asyncPush(IEventArg eventArg) {
        FutureTask<EventHandleStatusEnum> task = getFutureTask(eventArg);
        return task;
    }

    private static FutureTask<EventHandleStatusEnum> getFutureTask(IEventArg eventArg) {
        if (Objects.isNull(eventArg) || !eventArg.isLocal()) {
            log.error("事件发送失败:事件参数为空，或isLocal为false");
            return null;
        }
        Callable<EventHandleStatusEnum> callable = () -> {
            if (Objects.isNull(eventArg)|| !eventArg.isLocal()) {
                log.error("事件发送失败:事件参数为空，或isLocal为false");
                return EventHandleStatusEnum.FAIL;
            }
            IEventRegistry eventRegistry = SpringUtil.getBean(IEventRegistry.class);
            if (Objects.isNull(eventRegistry)) {
                return EventHandleStatusEnum.FAIL;
            }
            Set<IHandler> handlers = eventRegistry.getEventArgHandlers(eventArg.getClass());
            if (CollectionUtil.isEmpty(handlers)) {
                return EventHandleStatusEnum.DEADEVENT;
            }
            int successCount = 0;
            for (IHandler handler : handlers) {
                try {
                    handler.handleEvent(eventArg);
                    successCount++;
                } catch (Exception e) {
                    log.error("事件处理器[{}]处理失败", handler.getClass().getName(), e);
                }
            }
            if (successCount == handlers.size()) {
                return EventHandleStatusEnum.SUCCESS;
            } else if (successCount > 0) {
                return EventHandleStatusEnum.PARTIAL_SUCCESS;
            } else {
                return EventHandleStatusEnum.FAIL;
            }
        };

        FutureTask<EventHandleStatusEnum> futureTask = new FutureTask<>(callable);

        Map<String, ThreadPoolTaskExecutor> threadPoolTaskExecutorMap = SpringUtil.getBeansOfType(ThreadPoolTaskExecutor.class);
        if (CollectionUtil.isEmpty(threadPoolTaskExecutorMap)) {
            EventBus.COMMON_POOL.submit(futureTask);
            return futureTask;
        }

        ExecutorService executorService = null;
        if (threadPoolTaskExecutorMap.size() > 1) {
            for (Map.Entry<String, ThreadPoolTaskExecutor> entry : threadPoolTaskExecutorMap.entrySet()) {
                if (entry.getKey().contains("event")) {
                    executorService = (ExecutorService) entry.getValue();
                    break;
                }
            }
        }
        if (Objects.isNull(executorService)) {
            executorService = (ExecutorService) threadPoolTaskExecutorMap.values().iterator().next();
        }
        executorService.submit(futureTask);
        return futureTask;
    }

    private static final ThreadPoolExecutor COMMON_POOL = (ThreadPoolExecutor) Executors.newCachedThreadPool();
}
