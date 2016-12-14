package com.atomist.rug.deployer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atomist.source.Deltas;
import com.atomist.source.FileArtifact;

public class DefaultDeployerEventListener implements DeployerEventListener {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(DefaultDeployerEventListener.class);

    @Override
    public void metadataGenerationStarted() {
        LOGGER.info("Metadata generation started");
    }

    @Override
    public void metadataFileGenerated(FileArtifact file) {
        LOGGER.info("Metadata file {} generated", file);
    }

    @Override
    public void metadataGenerationFinished() {
        LOGGER.info("Metadata generation finished");
    }

    @Override
    public void compilationStarted() {
        LOGGER.info("Compilation started");
    }

    @Override
    public void compilationFinished(Deltas deltas) {
        LOGGER.info("Compilation finished");
    }

}
