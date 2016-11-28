package com.atomist.rug.resolver;

import java.net.URI;

public class LocalArtifactDescriptor extends DefaultArtifactDescriptor {

    public LocalArtifactDescriptor(String group, String artifact, String version,
            Extension extension, Scope scope, URI uri) {
        super(group, artifact, version, extension, scope, uri);
    }

}
