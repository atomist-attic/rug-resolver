package com.atomist.rug.resolver;

public class LocalArtifactDescriptor extends DefaultArtifactDescriptor {

    public LocalArtifactDescriptor(String group, String artifact, String version,
            Extension extension, Scope scope, String uri) {
        super(group, artifact, version, extension, scope, uri);
    }
    
    @Override
    public String toString() {
        return super.toString() + ":local";
    }

}
