package com.atomist.rug.loader;

import com.atomist.project.archive.Operations;

public class OperationsAndHandlers {
    
    private final Operations operations;
    private final Handlers handlers;
    
    public OperationsAndHandlers(Operations operations, Handlers handlers) {
        this.operations = operations;
        this.handlers = handlers;
    }

    public Operations operations() {
        return operations;
    }
    
    public Handlers handlers() {
        return handlers;
    }
}
