package com.atomist.rug.loader;

import java.util.List;
import java.util.stream.Collectors;

import com.atomist.event.SystemEventHandler;

public class Handlers {
    
    private final List<SystemEventHandler> handlers;
    
    public Handlers(List<SystemEventHandler> handlers) {
        this.handlers = handlers;
    }
    
    public List<SystemEventHandler> handlers() {
        return handlers;
    }
    
    public List<String> handlerNames() {
        return handlers.stream().map(h -> h.name()).collect(Collectors.toList());
    }
    
}
