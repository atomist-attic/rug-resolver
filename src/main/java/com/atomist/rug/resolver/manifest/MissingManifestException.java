package com.atomist.rug.resolver.manifest;

public class MissingManifestException extends RuntimeException {

    private static final long serialVersionUID = -5616066311972886869L;

    public MissingManifestException(String msg, Exception e) {
        super(msg, e);
    }

    public MissingManifestException(String msg, String... tokens) {
        super(String.format(msg, (Object[]) tokens));
    }
}
