package com.atomist.rug.resolver.deployer;

import java.io.File;
import java.io.IOException;

import com.atomist.project.archive.Rugs;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.source.ArtifactSource;

public interface Deployer {

    void deploy(Rugs operationsAndHandlers, ArtifactSource source, ArtifactDescriptor artifact,
            File root) throws IOException;

    void registerEventListener(DeployerEventListener listener);

}