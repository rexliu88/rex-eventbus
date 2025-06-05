package com.bird.eventbus;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.bird.eventbus.arg.IEventArg;
import com.bird.eventbus.handler.AbstractHandler;
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
    private static final ThreadPoolExecutor COMMON_POOL = (ThreadPoolExecutor) Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("eventbus-tdd-" + t.getId());
        t.setDaemon(true); // 避免阻塞JVM退出
        return t;
    });
    private static volatile ExecutorService cachedExecutorService;
    private static volatile IEventRegistry cachedEventRegistry;

    public static <E extends IEventArg> void push(E eventArg) {
        if(eventArg == null || !eventArg.isLocal()) {
            log.error("事件发送失败:事件参数为空，或isLocal为false");
            return;
        }
        final String eventJson = JSONUtil.toJsonStr(eventArg);
        final String eventClassName = eventArg.getClass().getName();

        FutureTask<EventHandleStatusEnum> task = asyncPush(eventArg,eventJson,eventClassName);

        log.debug("事件处理开始：参数类：{}，参数值：{}", eventClassName, eventJson);

        if (task == null) {
            log.error("事件处理失败: 执行任务为空：{}, 参数值：{}", eventClassName, eventJson);
            return;
        }

        // 异步执行，不再阻塞主线程
        log.info("事件已异步提交：{}", eventClassName);

        EventHandleStatusEnum result;
        try {
            // 设置合理超时时间，防止永久阻塞
            result = task.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException e) {
            log.error("事件消费失败：抛出异常：", e);
            result = EventHandleStatusEnum.FAIL;
        } catch (TimeoutException e) {
            result = EventHandleStatusEnum.TIMEOUT;
        }

        switch (result) {
            case SUCCESS:
                log.info("事件处理成功:执行返回成功：参数类：{}，参数值：{}", eventClassName, eventJson);
                break;
            case FAIL:
                log.error("事件处理失败:执行返回失败：参数类：{}，参数值：{}", eventClassName, eventJson);
                break;
            case DEADEVENT:
                log.warn("事件处理失败:死信：参数类：{}，参数值：{}", eventClassName, eventJson);
                break;
            case PARTIAL_SUCCESS:
                log.warn("事件处理失败:部分成功：参数类：{}，参数值：{}", eventClassName, eventJson);
                break;
            case TIMEOUT:
                log.warn("事件处理失败:超时：参数类：{}，参数值：{}", eventClassName, eventJson);
                break;
        }
        log.info("事件处理结束：参数类：{}，参数值：{}", eventClassName, eventJson);
    }

    public static <E extends IEventArg> FutureTask<EventHandleStatusEnum> asyncPush(E eventArg,final String eventJson,final String eventClassName) {
        return getFutureTask(eventArg,eventJson,eventClassName);
    }

    private static <E extends IEventArg> FutureTask<EventHandleStatusEnum> getFutureTask(E eventArg,final String eventJson,final String eventClassName) {
        if (Objects.isNull(eventArg) || !eventArg.isLocal()) {
            log.error("事件发送失败:事件参数为空，或isLocal为false");
            return null;
        }
        log.info("事件发送开始：{}", eventJson);
        Callable<EventHandleStatusEnum> callable = () -> {
            if (Objects.isNull(eventArg) || !eventArg.isLocal()) {
                log.error("事件发送失败:事件参数为空，或isLocal为false");
                return EventHandleStatusEnum.FAIL;
            }
            IEventRegistry eventRegistry = getCachedEventRegistry();
            if (Objects.isNull(eventRegistry)) {
                log.error("事件注册器:为空。参数类：{}，参数值：{}",eventClassName, eventJson);
                return EventHandleStatusEnum.FAIL;
            }
            Set<IHandler> handlers = eventRegistry.getEventArgHandlers(eventArg.getClass());
            if (CollectionUtil.isEmpty(handlers)) {
                log.info("事件处理失败:死信:无处理器,参数类：{}，参数值：{}",eventClassName, eventJson);
                return EventHandleStatusEnum.DEADEVENT;
            }
            int successCount = 0;
            for (IHandler handler : handlers) {
                try {
                    handler.HandleEvent(eventArg);
                    successCount++;
                    log.info("事件处理器[{}]处理成功：参数类：{}，参数值：{}",eventClassName, eventJson);
                } catch (Exception e) {
                    log.error("事件处理器[{}]处理失败：参数类：{}，参数值：{}",e.getMessage(),eventClassName, eventJson);
                }
            }
            if (successCount == handlers.size()) {
                log.info("事件处理:全部成功,参数类：{}，参数值：{}",eventClassName, eventJson);
                return EventHandleStatusEnum.SUCCESS;
            } else if (successCount > 0) {
                log.warn("事件处理:部分成功,参数类：{}，参数值：{}",eventClassName, eventJson);
                return EventHandleStatusEnum.PARTIAL_SUCCESS;
            } else {
                log.error("事件处理:全部失败,参数类：{}，参数值：{}",eventClassName, eventJson);
                return EventHandleStatusEnum.FAIL;
            }
        };
        FutureTask<EventHandleStatusEnum> futureTask = new FutureTask<>(callable);
        ExecutorService executorService = getCachedExecutorService();

        try {
            executorService.submit(futureTask);
        } catch (RejectedExecutionException e) {
            log.error("线程池拒绝任务提交", e);
            if(cachedExecutorService != null && cachedExecutorService != COMMON_POOL) {
                COMMON_POOL.submit(futureTask);
            }
        }
        log.info("事件发送结束：{}", eventJson);
        return futureTask;
    }

    private static IEventRegistry getCachedEventRegistry() {
        if (cachedEventRegistry != null) {
            return cachedEventRegistry;
        }
        synchronized (EventBus.class) {
            if (cachedEventRegistry != null) {
                return cachedEventRegistry;
            }
            IEventRegistry selected = null;
            Map<String, IEventRegistry> registryMap = SpringUtil.getBeansOfType(IEventRegistry.class);
            if (CollectionUtil.isNotEmpty(registryMap)) {
                selected = registryMap.values().iterator().next();
            }
            if (Objects.isNull(selected)) {
                log.error("事件注册器:为空。");
            }
            cachedEventRegistry = selected;
            return cachedEventRegistry;
        }
    }

    private static ExecutorService getCachedExecutorService() {
        if (cachedExecutorService != null) {
            return cachedExecutorService;
        }

        synchronized (EventBus.class) {
            if (cachedExecutorService != null) {
                return cachedExecutorService;
            }

            ExecutorService selected = null;

//            Map<String, ThreadPoolTaskExecutor> threadPoolTaskExecutorMap = SpringUtil.getBeansOfType(ThreadPoolTaskExecutor.class);
//            if (CollectionUtil.isNotEmpty(threadPoolTaskExecutorMap)) {
//                for (Map.Entry<String, ThreadPoolTaskExecutor> entry : threadPoolTaskExecutorMap.entrySet()) {
//                    if (entry.getKey().contains("event") && entry.getValue().getCorePoolSize() > 50) {
//                        selected = entry.getValue().getThreadPoolExecutor();
//                        break;
//                    }
//                }
//                if (selected == null) {
//                    ThreadPoolTaskExecutor defaultExecutor = threadPoolTaskExecutorMap.values().iterator().next();
//                    if (defaultExecutor.getCorePoolSize() > 50) {
//                        selected = defaultExecutor.getThreadPoolExecutor();
//                    }
//                }
//            }

            if(selected == null) {
                selected = COMMON_POOL;
            }
            cachedExecutorService = selected;
            return cachedExecutorService;
        }
    }
    public static void shutdown() {
        if (cachedExecutorService != null) {
            cachedExecutorService.shutdownNow();
        }
        if (!COMMON_POOL.isShutdown()) {
            COMMON_POOL.shutdownNow();
        }
    }
}
