package com.bird.eventbus.handler;

import com.bird.eventbus.arg.IEventArg;

public interface IHandler {
    void HandleEvent(IEventArg eventArg);
}
