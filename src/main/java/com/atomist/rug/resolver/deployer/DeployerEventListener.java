package com.atomist.rug.resolver.deployer;

import com.atomist.source.FileArtifact;

public interface DeployerEventListener {
    void metadataFileGenerated(FileArtifact fileName);

    void metadataGenerationFinished();

    void metadataGenerationStarted();
}
