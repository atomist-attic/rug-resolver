package com.atomist.rug.resolver.deployer;

import com.atomist.project.archive.Rugs;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.source.ArtifactSource;

import java.io.File;
import java.io.IOException;

public interface Deployer {

    void registerEventListener(DeployerEventListener listener);

    void deploy(Rugs operationsAndHandlers, ArtifactSource source, ArtifactDescriptor artifact,
            File root) throws IOException;

}