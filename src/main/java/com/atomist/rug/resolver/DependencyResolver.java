package com.atomist.rug.resolver;

import java.util.List;

public interface DependencyResolver {

    String resolveVersion(ArtifactDescriptor artifact) throws DependencyResolverException;

    List<ArtifactDescriptor> resolveTransitiveDependencies(ArtifactDescriptor artifact)
            throws DependencyResolverException;

    List<ArtifactDescriptor> resolveDirectDependencies(ArtifactDescriptor artifact)
            throws DependencyResolverException;

}
