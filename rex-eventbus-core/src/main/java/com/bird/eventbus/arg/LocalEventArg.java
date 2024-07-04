package com.bird.eventbus.arg;

public class LocalEventArg extends EventArg {
    @Override
    public Boolean isLocal() {
        return true;
    }
}
