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
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@NoArgsConstructor
public class EventBus {
    public static <E extends IEventArg> void push(E eventArg) {
        log.info("事件发送开始。");
        FutureTask<EventHandleStatusEnum> task = asyncPush(eventArg);
        log.info("事件处理开始。");
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

    public static <E extends IEventArg> FutureTask<EventHandleStatusEnum> asyncPush(E eventArg) {
        FutureTask<EventHandleStatusEnum> task = getFutureTask(eventArg);
        return task;
    }

    private static <E extends IEventArg> FutureTask<EventHandleStatusEnum> getFutureTask(E eventArg) {
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
                log.error("事件注册器:为空。" );
                return EventHandleStatusEnum.FAIL;
            }
            Set<IHandler> handlers = eventRegistry.getEventArgHandlers(eventArg.getClass());
            if (CollectionUtil.isEmpty(handlers)) {
                log.error("事件处理失败:死信，无处理器,参数类：{}，参数值：{}" , eventArg.getClass().getName(), JSONUtil.toJsonStr(eventArg));
                return EventHandleStatusEnum.DEADEVENT;
            }
            int successCount = 0;
            for (IHandler handler : handlers) {
                try {
                    handler.HandleEvent(eventArg);
                    successCount++;
                    log.info("事件处理器[{}]处理成功,参数类：{}，参数值：{}" , eventArg.getClass().getName(), JSONUtil.toJsonStr(eventArg));
                } catch (Exception e) {
                    log.error("事件处理器[{}]处理失败", handler.getClass().getName(), e);
                }
            }
            if (successCount == handlers.size()) {
                log.info("事件处理:全部成功,参数类：{}，参数值：{}" , eventArg.getClass().getName(), JSONUtil.toJsonStr(eventArg));
                return EventHandleStatusEnum.SUCCESS;
            } else if (successCount > 0) {
                log.warn("事件处理:部分成功,参数类：{}，参数值：{}" , eventArg.getClass().getName(), JSONUtil.toJsonStr(eventArg));
                return EventHandleStatusEnum.PARTIAL_SUCCESS;
            } else {
                log.error("事件处理:全部失败,参数类：{}，参数值：{}" , eventArg.getClass().getName(), JSONUtil.toJsonStr(eventArg));
                return EventHandleStatusEnum.FAIL;
            }
        };

        FutureTask<EventHandleStatusEnum> futureTask = new FutureTask<>(callable);

        //Map<String, ThreadPoolTaskExecutor> threadPoolTaskExecutorMap = SpringUtil.getBeansOfType(ThreadPoolTaskExecutor.class);
        //if (CollectionUtil.isEmpty(threadPoolTaskExecutorMap)) {
        //    EventBus.COMMON_POOL.submit(futureTask);
        //    return futureTask;
        //}

        //ExecutorService executorService = null;
        //if (threadPoolTaskExecutorMap.size() > 1) {
        //    for (Map.Entry<String, ThreadPoolTaskExecutor> entry : threadPoolTaskExecutorMap.entrySet()) {
        //        if (entry.getKey().contains("event")) {
        //            executorService = (ExecutorService) entry.getValue().getThreadPoolExecutor();
        //            break;
        //        }
        //    }
        //}
        //if (Objects.isNull(executorService)) {
        //    executorService = (ExecutorService) threadPoolTaskExecutorMap.values().iterator().next().getThreadPoolExecutor();
        //}
        //executorService.submit(futureTask);
        EventBus.COMMON_POOL.submit(futureTask);
        return futureTask;
    }

    private static final ThreadPoolExecutor COMMON_POOL = (ThreadPoolExecutor) newCachedThreadPool();

    public static ExecutorService newCachedThreadPool() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        if (corePoolSize < 2) {
            corePoolSize = 5;
        }
        if (corePoolSize > 8) {
            corePoolSize = 8;
        }
        int maxPoolSize =corePoolSize * 2 +1;
        long keepAliveTime = 60;
        TimeUnit unit = TimeUnit.SECONDS;
        int queueSize = maxPoolSize * 2 + 1;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(queueSize);
        String namePrefix = "event-bus-thread-pool-";
        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName(namePrefix + threadNumber.getAndIncrement());
                return t;
            }
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize, maxPoolSize,
                keepAliveTime, unit, workQueue, threadFactory);
        executor.setRejectedExecutionHandler(handler);

        return executor;
    }
}
