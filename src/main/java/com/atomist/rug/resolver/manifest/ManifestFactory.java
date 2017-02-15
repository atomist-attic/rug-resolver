package com.atomist.rug.resolver.manifest;

import com.atomist.source.ArtifactSource;

public abstract class ManifestFactory {
    
    public static Manifest read(ArtifactSource source) {
        
        if (source.findFile(".atomist/manifest.yml").isDefined()) {
            Manifest manifest = new ManifestReader().read(source);
            if (manifest != null) {
                return manifest;
            }
        }
        throw new MissingManifestException("No manifest.yml found in .atomist folder");
    }

}
