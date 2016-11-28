package com.atomist.rug.manifest;

import com.atomist.source.ArtifactSource;

public abstract class ManifestFactory {
    
    public static Manifest read(ArtifactSource source) {
        
        if (source.findFile(".atomist/manifest.yml").isDefined()) {
            Manifest manifest = new ManifestReader().read(source);
            if (manifest != null) {
                return manifest;
            }
        }
        else if (source.findFile(".atomist/package.json").isDefined()) {
            Manifest manifest = new PackageJsonToManifestReader().read(source);
            if (manifest != null) {
                return manifest;
            }
        }

        throw new ManifestException("No manifest.yml or package.json found in .atomist folder");
    }

}
