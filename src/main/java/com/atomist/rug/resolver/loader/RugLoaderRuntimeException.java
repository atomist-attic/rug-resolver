package com.atomist.rug.resolver.loader;

public class RugLoaderRuntimeException extends RugLoaderException {

    private static final long serialVersionUID = -1788230882200234770L;

    public RugLoaderRuntimeException(String msg, Exception e) {
        super(msg, e);
    }
}
