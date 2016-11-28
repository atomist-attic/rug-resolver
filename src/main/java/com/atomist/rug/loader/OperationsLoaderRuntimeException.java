package com.atomist.rug.loader;

public class OperationsLoaderRuntimeException extends OperationsLoaderException {

    private static final long serialVersionUID = -1788230882200234770L;

    public OperationsLoaderRuntimeException(String msg, Exception e) {
        super(msg, e);
    }
}
