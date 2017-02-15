package com.atomist.rug.resolver.loader;

import com.atomist.project.archive.Rugs;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.source.ArtifactSource;

public interface RugLoader {

    Rugs load(ArtifactDescriptor artifact, ArtifactSource source)
            throws RugLoaderException;

    Rugs load(String group, String artifact, String version, ArtifactSource source)
            throws RugLoaderException;

    Rugs load(ArtifactDescriptor artifact)
            throws RugLoaderException;
    
    Rugs load(String group, String artifact, String version)
            throws RugLoaderException;

}