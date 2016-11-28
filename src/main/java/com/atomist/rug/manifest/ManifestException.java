package com.atomist.rug.manifest;

public class ManifestException extends RuntimeException {

    private static final long serialVersionUID = -5616066311972886869L;

    public ManifestException(String msg, String... tokens) {
        super(String.format(msg, (Object[]) tokens));
    }
    
    public ManifestException(String msg, Exception e) {
        super(msg, e);
    }
}
