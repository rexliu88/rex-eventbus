package com.bird.eventbus;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
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
    private static final ThreadPoolExecutor COMMON_POOL = (ThreadPoolExecutor) Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("eventbus-tdd-" + t.getId());
        t.setDaemon(true); // 避免阻塞JVM退出
        return t;
    });
    private static volatile ExecutorService cachedExecutorService;
    private static volatile IEventRegistry cachedEventRegistry;
    private static final Object eventBusLock = new Object();
    private static final Object eventBusLock2 = new Object();

    public static <E extends IEventArg> void push(E eventArg) {
        if(eventArg == null || !eventArg.isLocal()) {
            log.error("事件发送失败:事件参数为空，或isLocal为false");
            return;
        }
        final String eventJson = JSONUtil.toJsonStr(eventArg);
        final String eventClassName = eventArg.getClass().getName();

        CompletableFuture<EventHandleStatusEnum> completableFuture = asyncPush(eventArg, eventJson, eventClassName);

        log.debug("事件处理开始：参数类：{}，参数值：{}", eventClassName, eventJson);

        if (completableFuture == null) {
            log.error("事件处理失败: 执行任务为空：{}, 参数值：{}", eventClassName, eventJson);
            return;
        }

        log.info("事件已异步提交：{}", eventClassName);

        completableFuture.exceptionally(ex -> {
            log.error("事件消费失败2", ex);
            return EventHandleStatusEnum.FAIL;
        }).thenAccept(status -> {
            log.info("事件处理结束:{}", status);
            switch (status) {
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
        });

        EventHandleStatusEnum result = EventHandleStatusEnum.FAIL;
        try {
            // 设置合理超时时间，防止永久阻塞
            result = completableFuture.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException e) {
            log.error("事件消费失败：抛出异常：", e);
            result = EventHandleStatusEnum.FAIL;
        } catch (TimeoutException e) {
            result = EventHandleStatusEnum.TIMEOUT;
        }
        log.info("事件处理结束：参数类：{}，参数值：{}", eventClassName, eventJson);
    }

    public static <E extends IEventArg> CompletableFuture<EventHandleStatusEnum> asyncPush(E eventArg) {
        if(eventArg == null || !eventArg.isLocal()) {
            log.error("事件发送失败:事件参数为空，或isLocal为false");
            return CompletableFuture.completedFuture(EventHandleStatusEnum.FAIL);
        }
        final String eventJson = JSONUtil.toJsonStr(eventArg);
        final String eventClassName = eventArg.getClass().getName();
        CompletableFuture<EventHandleStatusEnum> completableFuture = asyncPush(eventArg, eventJson, eventClassName);
        return completableFuture;
    }

    public static <E extends IEventArg> CompletableFuture<EventHandleStatusEnum> asyncPush(E eventArg,final String eventJson,final String eventClassName) {
        CompletableFuture<EventHandleStatusEnum> completableFuture = getCompletableFuture(eventArg,eventJson,eventClassName);
        return completableFuture;
    }

    private static <E extends IEventArg> CompletableFuture<EventHandleStatusEnum> getCompletableFuture(E eventArg,final String eventJson,final String eventClassName) {
        if (Objects.isNull(eventArg) || !eventArg.isLocal()) {
            log.error("事件发送失败:事件参数为空，或isLocal为false");
            return CompletableFuture.completedFuture(EventHandleStatusEnum.FAIL);
        }
        log.info("事件发送开始：参数类：{}，参数值：{}", eventClassName, eventJson);
        CompletableFuture<EventHandleStatusEnum> completableFuture = new CompletableFuture<>();

        Callable<EventHandleStatusEnum> callable = () -> {
            if (Objects.isNull(eventArg) || !eventArg.isLocal()) {
                log.error("事件发送失败:事件参数为空，或isLocal为false");
                return EventHandleStatusEnum.FAIL;
            }
            IEventRegistry eventRegistry = getCachedEventRegistry();
            Set<IHandler> handlers = eventRegistry.getEventArgHandlers(eventArg.getClass());
            if (CollectionUtil.isEmpty(handlers)) {
                log.info("事件处理失败:死信:无处理器,参数类：{}，参数值：{}",eventClassName, eventJson);
                return EventHandleStatusEnum.DEADEVENT;
            }
            int successCount = 0;
            for (IHandler handler : handlers) {
                String handlerName = handler.getClass().getName();
                try {
                    handler.HandleEvent(eventArg);
                    successCount++;
                    log.info("事件处理器[{}]处理成功：参数类：{}，参数值：{}", handlerName, eventClassName, eventJson);
                } catch (Exception e) {
                    log.error("事件处理器[{}]处理失败：参数类：{}，参数值：{}", handlerName, eventClassName, eventJson, e);
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
        ExecutorService executorService = getCachedExecutorService();
        executorService.submit(() -> {
            try {
                EventHandleStatusEnum result = callable.call();
                completableFuture.complete(result); // 完成 Future 并设置结果
            } catch (Exception e) {
                completableFuture.completeExceptionally(e); // 异常处理
                log.error("cached线程池拒绝任务提交", e);
                if(cachedExecutorService != null && cachedExecutorService != COMMON_POOL) {
                    COMMON_POOL.submit(() -> {
                        try {
                            EventHandleStatusEnum result = callable.call();
                            completableFuture.complete(result); // 完成 Future 并设置结果
                        } catch (Exception e2) {
                            completableFuture.completeExceptionally(e2); // 异常处理
                            log.error("COMMON_POOL线程池拒绝任务提交", e);
                        }
                    });
                }
            }
        });
        log.info("事件发送结束：{}", eventJson);
        return completableFuture;
    }
    private static IEventRegistry getCachedEventRegistry() {
        if (cachedEventRegistry != null) {
            return cachedEventRegistry;
        }
        synchronized (eventBusLock2) {
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

        synchronized (eventBusLock) {
            if (cachedExecutorService != null) {
                return cachedExecutorService;
            }

            ExecutorService selected = null;

            Map<String, ThreadPoolTaskExecutor> threadPoolTaskExecutorMap = SpringUtil.getBeansOfType(ThreadPoolTaskExecutor.class);
            if (CollectionUtil.isNotEmpty(threadPoolTaskExecutorMap)) {
                for (Map.Entry<String, ThreadPoolTaskExecutor> entry : threadPoolTaskExecutorMap.entrySet()) {
                    ThreadPoolTaskExecutor defaultExecutor = entry.getValue();
                    defaultExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
                    if (entry.getKey().contains("event") && defaultExecutor.getCorePoolSize() > 50) {
                        selected = entry.getValue().getThreadPoolExecutor();
                        break;
                    }
                }
                if (selected == null) {
                    ThreadPoolTaskExecutor defaultExecutor = threadPoolTaskExecutorMap.values().iterator().next();
                    if (defaultExecutor.getCorePoolSize() > 50) {
                        selected = defaultExecutor.getThreadPoolExecutor();
                    }
                }
            }

            if(selected == null) {
                selected = COMMON_POOL;
            }
            cachedExecutorService = selected;
            return cachedExecutorService;
        }
    }
    public static void shutdown() {
        if (cachedExecutorService != null && cachedExecutorService != COMMON_POOL) {
            cachedExecutorService.shutdown();
            try {
                if (!cachedExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    cachedExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                cachedExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (!COMMON_POOL.isShutdown()) {
            COMMON_POOL.shutdown();
            try {
                if (!COMMON_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                    COMMON_POOL.shutdownNow();
                }
            } catch (InterruptedException e) {
                COMMON_POOL.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
