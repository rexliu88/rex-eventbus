package com.bird.eventbus;

import com.bird.eventbus.arg.IEventArg;
import com.bird.eventbus.handler.AsyncEventMethodInvoker;
import com.bird.eventbus.handler.EventHandleStatusEnum;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

@Slf4j
@NoArgsConstructor
public class EventBus {
    public void push(IEventArg eventArg) {
        log.info("事件发送开始。");
        EventBus.syncPush(eventArg);
        log.info("事件发送结束！");
    }

    public static void syncPush(IEventArg eventArg) {
        if (eventArg.isLocal()) {
            // 返回运行状态
            AsyncEventMethodInvoker asyncEventMethodInvoker = new AsyncEventMethodInvoker();
            asyncEventMethodInvoker.setEventArg(eventArg);
            FutureTask<EventHandleStatusEnum> task = new FutureTask<EventHandleStatusEnum>(asyncEventMethodInvoker);
            new Thread(task).start();
            EventHandleStatusEnum eventHandleStatusEnum = EventHandleStatusEnum.FAIL;
            try {
                eventHandleStatusEnum = task.get();
            } catch (InterruptedException e) {
                log.error("事件处理失败",e);
            } catch (ExecutionException e) {
                log.error("事件消费失败1", e);
            }
            if (eventHandleStatusEnum == EventHandleStatusEnum.FAIL) {
                log.error("事件处理失败");
            }
        }
    }

    public static FutureTask asyncPush(IEventArg eventArg) {
        if (eventArg.isLocal()) {
            AsyncEventMethodInvoker asyncEventMethodInvoker = new AsyncEventMethodInvoker();
            asyncEventMethodInvoker.setEventArg(eventArg);
            FutureTask<EventHandleStatusEnum> task = new FutureTask<EventHandleStatusEnum>(asyncEventMethodInvoker);
            new Thread(task).start();
            return task;
        }
        return null;
    }
}
