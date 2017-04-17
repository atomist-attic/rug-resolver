package com.atomist.rug.resolver;

import com.atomist.rug.resolver.ArtifactDescriptor.Extension;
import com.atomist.rug.resolver.ArtifactDescriptor.Scope;

public class ArtifactDescriptorFactory {

    public static ArtifactDescriptor copyFrom(ArtifactDescriptor artifact, String version) {
        DefaultArtifactDescriptor newArtifact;
        if (artifact instanceof LocalArtifactDescriptor) {
            newArtifact = new LocalArtifactDescriptor(artifact.group(), artifact.artifact(),
                    version, artifact.extension(), artifact.scope(), artifact.uri());
        }
        else {
            newArtifact = new DefaultArtifactDescriptor(artifact.group(), artifact.artifact(),
                    version, artifact.extension(), artifact.scope(), artifact.uri());
        }
        artifact.dependencies().forEach(d -> newArtifact.addDependency(
                copyFromParent(artifact, d, artifact.group(), artifact.artifact(), version)));
        return newArtifact;
    }

    public static ArtifactDescriptor copyFrom(ArtifactDescriptor a, String group, String artifact,
            String version) {
        String _group = (group != null ? group : a.group());
        String _artifact = (artifact != null ? artifact : a.artifact());
        String _version = (version != null ? version : a.version());

        DefaultArtifactDescriptor newArtifact;

        if (a instanceof LocalArtifactDescriptor) {
            newArtifact = new LocalArtifactDescriptor(_group, _artifact, _version, a.extension(),
                    a.scope(), a.uri());
        }
        else {
            newArtifact = new DefaultArtifactDescriptor(_group, _artifact, _version, a.extension(),
                    a.scope(), a.uri());
        }
        a.dependencies().forEach(
                d -> newArtifact.addDependency(copyFromParent(a, d, _group, _artifact, _version)));
        return newArtifact;

    }

    public static ArtifactDescriptor copyFrom(ArtifactDescriptor a, String group, String artifact,
            String version, Extension ext) {
        String _group = (group != null ? group : a.group());
        String _artifact = (artifact != null ? artifact : a.artifact());
        String _version = (version != null ? version : a.version());

        DefaultArtifactDescriptor newArtifact;

        if (a instanceof LocalArtifactDescriptor) {
            newArtifact = new LocalArtifactDescriptor(_group, _artifact, _version, ext, a.scope(),
                    a.uri());
        }
        else {
            newArtifact = new DefaultArtifactDescriptor(_group, _artifact, _version, ext, a.scope(),
                    a.uri());
        }
        a.dependencies().forEach(
                d -> newArtifact.addDependency(copyFromParent(a, d, _group, _artifact, _version)));
        return newArtifact;
    }

    public static ArtifactDescriptor create(String artifact, String version) {
        if (version == null) {
            version = "latest";
        }
        String[] segments = artifact.split(":");
        if (segments.length == 2) {
            return new DefaultArtifactDescriptor(segments[0], segments[1], version, Extension.JAR);
        }
        else if (segments.length == 3) {
            return new DefaultArtifactDescriptor(segments[0], segments[1], version,
                    toExtension(segments[2]));
        }
        throw new IllegalArgumentException(String.format(
                "Specified artifact %s should be of format <group>:<artifact>(:<extension>)",
                artifact));
    }

    public static Extension toExtension(String extension) {
        return Extension.valueOf(extension.toUpperCase().replace(".", "_"));
    }

    public static Scope toScope(String scope) {
        return Scope.valueOf(scope.toUpperCase());
    }

    private static ArtifactDescriptor copyFromParent(ArtifactDescriptor parent,
            ArtifactDescriptor child, String group, String artifact, String version) {
        if (parent.group().equals(child.group()) && parent.artifact().equals(child.artifact())
                && parent.version().equals(child.version())) {
            return new DefaultArtifactDescriptor(group, artifact, version, child.extension(),
                    child.scope(), child.classifier(), child.uri());
        }
        return child;
    }
}
