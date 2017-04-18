package com.atomist.rug.resolver;

/**
 * Allows clients to verify artifacts before they are being put on the dependency graph. 
 */
public interface DependencyVerifier {
    
    boolean verify(ArtifactDescriptor artifact, ArtifactDescriptor signature,
            ArtifactDescriptor pom, ArtifactDescriptor pomSignature);

}
