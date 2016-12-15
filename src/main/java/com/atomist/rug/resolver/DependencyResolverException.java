package com.atomist.rug.resolver;

public class DependencyResolverException extends RuntimeException {

    private static final long serialVersionUID = 8152076269320243265L;

    public DependencyResolverException(String msg) {
        super(msg);
    }

    public DependencyResolverException(String msg, Exception e) {
        super(msg, e);
    }
}
