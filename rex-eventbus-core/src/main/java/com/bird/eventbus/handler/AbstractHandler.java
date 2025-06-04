package com.bird.eventbus.handler;

import com.bird.eventbus.arg.IEventArg;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractHandler<E extends IEventArg> implements IHandler<E>{
    public abstract void HandleEvent(E eventArg);

    @EventHandler
    public void onEvent(E event) {
        try {
            HandleEvent(event);
            // 成功处理
            log.info("handle event {} success", event.getClass());
        } catch (Exception e) {
            // 记录异常日志
            log.error(String.format("handle event %s exception 111111", new Object[]{event.getClass()}), e);
        }
    }
}
