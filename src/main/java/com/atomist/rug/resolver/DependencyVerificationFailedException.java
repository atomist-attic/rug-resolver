package com.atomist.rug.resolver;

/**
 * {@link Exception} indicating a dependency verification failed 
 */
public class DependencyVerificationFailedException extends DependencyResolverException {

    private static final long serialVersionUID = -197620673599106417L;
    
    private String group;
    private String artifact;
    private String version;

    public DependencyVerificationFailedException(String msg, String group, String artifact, String version) {
        super(msg);
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }
    
    public DependencyVerificationFailedException(String msg,String group, String artifact, String version, Exception e) {
        super(msg, e);
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }
    
    public String group() {
        return group;
    }

    public String artifact() {
        return artifact;
    }
    
    public String version() {
        return version;
    }
}
