package com.atomist.rug.resolver;

public interface DependencyVerificationListener {
    
    void starting(String group, String artifact, String version);
    
    void succeeded(String group, String artifact, String version);
    
    void failed(String group, String artifact, String version, Exception e);

}
