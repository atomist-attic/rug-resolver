package com.atomist.rug.resolver;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class DefaultArtifactDescriptor implements ArtifactDescriptor {

    private final String group;
    private final String artifact;
    private final String version;
    private final URI uri;
    private final Extension extension;
    private final Scope scope;
    private List<ArtifactDescriptor> dependencies = new ArrayList<>();
    
    public DefaultArtifactDescriptor(String group, String artifact, String version,
            Extension extension) {
        this(group, artifact, version, extension, Scope.COMPILE, null);
    }

    public DefaultArtifactDescriptor(String group, String artifact, String version,
            Extension extension, Scope scope, URI uri) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
        this.uri = uri;
        this.extension = extension;
        this.scope = scope;
    }

    @Override
    public String group() {
        return group;
    }

    @Override
    public String artifact() {
        return artifact;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public Extension extension() {
        return extension;
    }

    @Override
    public Scope scope() {
        return scope;
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public boolean match(String group, String artifact, String version, Extension extension) {
        return this.group.equals(group) && this.artifact.equals(artifact)
                && this.version.equals(version) && this.extension.equals(extension);
    }
    
    public List<ArtifactDescriptor> dependencies() {
        return dependencies;
    }

    public void addDependency(ArtifactDescriptor dependency) {
        dependencies.add(dependency);
    }

}
