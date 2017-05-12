package com.atomist.rug.resolver.manifest;

import com.atomist.source.ArtifactSource;

public abstract class ManifestFactory {

    public static Manifest read(ArtifactSource source) {

        Manifest manifest = new Manifest();
        boolean manifestFound = false;
        boolean packageJsonFound = false;

        if (source.findFile(".atomist/manifest.yml").isDefined()) {
            new ManifestReader().read(source, manifest);
            manifestFound = true;
        }

        if (source.findFile(".atomist/package.json").isDefined()) {
            new PackageJsonToManifestReader().read(source, manifest);
            packageJsonFound = true;
        }

        if (!manifestFound && !packageJsonFound) {
            throw new MissingManifestException(
                    "No valid Rug archive manifest found. Please add a package.json or manifest.yml.");
        }

        return ManifestValidator.validate(manifest);
    }

}
