package com.atomist.rug.deployer;

import java.io.File;
import java.io.IOException;

import com.atomist.rug.loader.OperationsAndHandlers;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.source.ArtifactSource;

public interface Deployer {

    void registerEventListener(DeployerEventListener listener);

    void deploy(OperationsAndHandlers operationsAndHandlers, ArtifactSource source,
            ArtifactDescriptor artifact, File root) throws IOException;

}