package com.atomist.rug.deployer;

import java.io.File;
import java.io.IOException;

import com.atomist.project.archive.Operations;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.source.ArtifactSource;

public interface Deployer {

    void deploy(Operations operations, ArtifactSource source, ArtifactDescriptor artifact,
            File root) throws IOException;

}