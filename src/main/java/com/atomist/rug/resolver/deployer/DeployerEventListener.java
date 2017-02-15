package com.atomist.rug.resolver.deployer;

import com.atomist.source.FileArtifact;

public interface DeployerEventListener {
    void metadataGenerationStarted();
    void metadataFileGenerated(FileArtifact fileName);
    void metadataGenerationFinished();
}
