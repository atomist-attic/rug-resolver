package com.atomist.rug.manifest;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.atomist.source.ArtifactSource;
import com.atomist.source.file.FileSystemArtifactSource;
import com.atomist.source.file.SimpleFileSystemArtifactSourceIdentifier;

public class PackageJsonToManifestReaderTest {

    @Test
    public void testValidParse() {
        ArtifactSource source = new FileSystemArtifactSource(
                new SimpleFileSystemArtifactSourceIdentifier(new File(
                        "./src/test/resources/typescript-editors")));
        Manifest manifest = new PackageJsonToManifestReader().read(source);
        assertEquals("typescript-editors", manifest.artifact());
        assertEquals("atomist-rugs", manifest.group());
        assertEquals("0.1.1", manifest.version());
        assertEquals("0.3.1", manifest.requires());
    }

}
