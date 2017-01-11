package com.atomist.rug.manifest;

import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionScheme;

public abstract class ManifestValidator {

    public static Manifest validate(Manifest manifest) {
        if (manifest.group() == null || manifest.group().length() == 0) {
            throw new ManifestException(
                    "group should not be empty. Please correct group in manifest.yml");
        }
        if (manifest.artifact() == null || manifest.artifact().length() == 0) {
            throw new ManifestException(
                    "artifact should not be empty. Please correct artifact in manifest.yml");
        }
        validateVersion(manifest.version(), "version");
        validateVersion(manifest.requires(), "requires");
        return manifest;
    }

    private static void validateVersion(String version, String key) {
        if (version == null) {
            throw new ManifestException(
                    "%s should not be empty. Please correct %s in manifest.yml", key,
                    key);
        }

        try {
            VersionScheme scheme = new GenericVersionScheme();
            scheme.parseVersionConstraint(version);
        }
        catch (InvalidVersionSpecificationException e) {
            throw new ManifestException(
                    "%s is not a valid version/version range. Please correct %s in manifest.yml",
                    version, key);
        }
    }

}
