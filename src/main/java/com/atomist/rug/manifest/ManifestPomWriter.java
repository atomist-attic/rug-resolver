package com.atomist.rug.manifest;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.ArtifactDescriptor.Extension;

public class ManifestPomWriter {

    public String write(Manifest manifest, ArtifactDescriptor artifact) {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        Model model = new Model();

        addProjectInformation(manifest, artifact, model);
        addRequiresDependency(manifest, model);
        addArchiveDependencies(manifest, model);
        addBinaryDependencies(manifest, model);
        addRepositories(manifest, model);

        StringWriter sw = new StringWriter();
        try {
            writer.write(sw, model);
        }
        catch (IOException e) {
        }
        return sw.toString();
    }

    private void addProjectInformation(Manifest manifest, ArtifactDescriptor artifact,
            Model model) {
        model.setGroupId(manifest.group());
        model.setArtifactId(manifest.artifact());
        model.setVersion(manifest.version());
        if (artifact.extension().equals(Extension.JAR)) {
            model.setPackaging("jar");
        }
        else {
            model.setPackaging("pom");
        }
        model.setName(manifest.artifact());
        model.setModelVersion("4.0.0");
    }

    private void addRepositories(Manifest manifest, Model model) {
        // add repository section
        manifest.repositories().forEach(r -> {
            Repository repo = new Repository();
            repo.setId(r.id());
            repo.setName(r.id());
            repo.setUrl(r.url());
            repo.setLayout("default");
            model.addRepository(repo);
        });
    }

    private void addBinaryDependencies(Manifest manifest, Model model) {
        // add extension types
        manifest.extensions().forEach(e -> {
            Dependency rugExtension = new Dependency();
            rugExtension.setGroupId(e.group());
            rugExtension.setArtifactId(e.artifact());
            rugExtension.setVersion(e.version());
            model.addDependency(rugExtension);
        });
    }

    private void addArchiveDependencies(Manifest manifest, Model model) {
        // add rug archive dependencies
        manifest.dependencies().forEach(d -> {
            Dependency rugArchive = new Dependency();
            rugArchive.setGroupId(d.group());
            rugArchive.setArtifactId(d.artifact());
            rugArchive.setVersion(d.version());
            rugArchive.setType("zip");
            model.addDependency(rugArchive);
            
            // add dependency on metadata.json to pom model
            Dependency rugArchiveMetadata = new Dependency();
            rugArchiveMetadata.setGroupId(d.group());
            rugArchiveMetadata.setArtifactId(d.artifact());
            rugArchiveMetadata.setVersion(d.version());
            rugArchiveMetadata.setType("json");
            rugArchiveMetadata.setClassifier("metadata");
            rugArchiveMetadata.setOptional(true);
            model.addDependency(rugArchiveMetadata);
        });
    }

    private void addRequiresDependency(Manifest manifest, Model model) {
        // add rug-lib dependency
        Dependency rugLib = new Dependency();
        rugLib.setGroupId("com.atomist");
        rugLib.setArtifactId("rug");
        rugLib.setVersion(manifest.requires());
        model.addDependency(rugLib);
    }

}
