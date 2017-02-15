package com.atomist.rug.resolver.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import com.atomist.source.ArtifactSource;
import com.atomist.source.file.FileSystemArtifactSource;
import com.atomist.source.file.SimpleFileSystemArtifactSourceIdentifier;

public class ManifestReaderTest {

    @Test
    public void testValidManifest() {
        ArtifactSource source = new FileSystemArtifactSource(
                new SimpleFileSystemArtifactSourceIdentifier(
                        new File(".", "src/test/resources/valid-manifest")));
         Manifest manifest = new ManifestReader().read(source);
         assertEquals("atomist-project-templates", manifest.group());
         assertEquals("spring-rest-service", manifest.artifact());
         assertEquals("3.5.0", manifest.version());
         assertEquals("1.1.1-20161015011603", manifest.requires());
         
         assertTrue(manifest.dependencies().size() == 2);
         assertEquals("atomist-project-templates", manifest.dependencies().get(0).group());
         assertEquals("common-editors", manifest.dependencies().get(0).artifact());
         assertEquals("2.1.0", manifest.dependencies().get(0).version());
         assertEquals("atomist", manifest.dependencies().get(1).group());
         assertEquals("bla", manifest.dependencies().get(1).artifact());
         assertEquals("1.1.0", manifest.dependencies().get(1).version());

         assertTrue(manifest.extensions().size() == 1);
         assertEquals("atomist", manifest.extensions().get(0).group());
         assertEquals("clj-rug", manifest.extensions().get(0).artifact());
         assertEquals("1.1.1", manifest.extensions().get(0).version());
    }

}
