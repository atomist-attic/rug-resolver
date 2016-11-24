package com.atomist.rug.deployer;

import java.io.File;
import java.io.IOException;

import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.source.ArtifactSource;

public interface Deployer {

    void deploy(File root, ArtifactSource source, ArtifactDescriptor artifact) throws IOException;

}