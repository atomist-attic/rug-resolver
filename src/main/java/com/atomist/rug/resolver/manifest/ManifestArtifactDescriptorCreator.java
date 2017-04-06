package com.atomist.rug.resolver.manifest;

import java.io.File;
import java.net.URI;

import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.ArtifactDescriptor.Extension;
import com.atomist.rug.resolver.ArtifactDescriptor.Scope;
import com.atomist.rug.resolver.DefaultArtifactDescriptor;
import com.atomist.rug.resolver.LocalArtifactDescriptor;

public class ManifestArtifactDescriptorCreator {

    public ArtifactDescriptor create(Manifest manifest, URI uri) {

        LocalArtifactDescriptor artifact = new LocalArtifactDescriptor(manifest.group(),
                manifest.artifact(), manifest.version(), Extension.ZIP, Scope.RUNTIME,
                new File(uri).getAbsolutePath());

        // add rug dependency
        artifact.addDependency(new DefaultArtifactDescriptor("com.atomist", "rug",
                manifest.requires(), Extension.JAR, Scope.COMPILE, null));

        // add rug archive dependencies
        manifest.dependencies().forEach(d -> {
            artifact.addDependency(new DefaultArtifactDescriptor(d.group(), d.artifact(),
                    d.version(), Extension.ZIP, Scope.COMPILE, null));
            artifact.addDependency(new DefaultArtifactDescriptor(d.group(), d.artifact(),
                    d.version(), Extension.JSON, Scope.COMPILE, "metadata", null));
        });

        // add extension types
        manifest.extensions().forEach(d -> {
            artifact.addDependency(new DefaultArtifactDescriptor(d.group(), d.artifact(),
                    d.version(), Extension.JAR, Scope.COMPILE, null));
        });
        return artifact;

    }

}
