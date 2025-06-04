package com.bird.eventbus.handler;

import com.bird.eventbus.arg.IEventArg;

public interface IHandler <E extends IEventArg> {
    void HandleEvent(E eventArg);
}
