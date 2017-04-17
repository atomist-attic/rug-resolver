package com.atomist.rug.resolver;

import java.util.List;

public interface DependencyResolver {

    List<ArtifactDescriptor> resolveDependencies(ArtifactDescriptor artifact,
            DependencyVerifier... verifiers) throws DependencyResolverException;

    ArtifactDescriptor resolveRugs(ArtifactDescriptor artifact) throws DependencyResolverException;

    String resolveVersion(ArtifactDescriptor artifact) throws DependencyResolverException;

}
