package com.atomist.rug.loader;

import com.atomist.project.archive.Operations;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.source.ArtifactSource;

public interface OperationsLoader {

    Operations load(ArtifactDescriptor artifact, ArtifactSource source)
            throws OperationsLoaderException;

    Operations load(String groug, String artifact, String version, ArtifactSource source)
            throws OperationsLoaderException;

    Operations load(ArtifactDescriptor artifact)
            throws OperationsLoaderException;
    
    Operations load(String groug, String artifact, String version)
            throws OperationsLoaderException;

}