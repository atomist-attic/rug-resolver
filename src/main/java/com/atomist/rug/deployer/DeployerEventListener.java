package com.atomist.rug.deployer;

import com.atomist.source.Deltas;
import com.atomist.source.FileArtifact;

public interface DeployerEventListener {
    
    void metadataGenerationStarted();
    void metadataFileGenerated(FileArtifact fileName);
    void metadataGenerationFinished();
    
    void compilationStarted();
    void compilationFinished(Deltas deltas);

}
