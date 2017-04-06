package com.atomist.rug.resolver;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultArtifactDescriptor implements ArtifactDescriptor {

    @JsonProperty
    private String artifact;
    @JsonProperty
    private String classifier;
    @JsonProperty
    private List<ArtifactDescriptor> dependencies = new ArrayList<>();
    @JsonProperty
    private Extension extension;
    @JsonProperty
    private String group;
    @JsonProperty
    private Scope scope;
    @JsonProperty
    private String uri;
    @JsonProperty
    private String version;

    public DefaultArtifactDescriptor() {

    }

    public DefaultArtifactDescriptor(String group, String artifact, String version,
            Extension extension) {
        this(group, artifact, version, extension, Scope.COMPILE, null, null);
    }

    public DefaultArtifactDescriptor(String group, String artifact, String version,
            Extension extension, Scope scope, String classifier, String uri) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
        this.uri = uri;
        this.extension = extension;
        this.scope = scope;
        this.classifier = classifier;
    }

    public DefaultArtifactDescriptor(String group, String artifact, String version,
            Extension extension, Scope scope, String uri) {
        this(group, artifact, version, extension, scope, null, uri);
    }

    public void addDependency(ArtifactDescriptor dependency) {
        dependencies.add(dependency);
    }

    @Override
    public String artifact() {
        return artifact;
    }

    @Override
    public String classifier() {
        return classifier;
    }

    public List<ArtifactDescriptor> dependencies() {
        return dependencies;
    }

    @Override
    public Extension extension() {
        return extension;
    }

    @Override
    public String group() {
        return group;
    }

    @Override
    public boolean match(String group, String artifact, String version, Extension extension) {
        return this.group.equals(group) && this.artifact.equals(artifact)
                && this.version.equals(version) && this.extension.equals(extension);
    }

    @Override
    public Scope scope() {
        return scope;
    }

    @Override
    public String uri() {
        return uri;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s:%s:%s:%s", group, artifact, version, extension, scope,
                classifier);
    }

}
