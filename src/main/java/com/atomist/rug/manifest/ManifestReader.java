package com.atomist.rug.manifest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;

import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;

import scala.Option;

class ManifestReader {

    @SuppressWarnings("unchecked")
    public Manifest read(ArtifactSource source) {
        Option<FileArtifact> manifestFile = source.findFile(".atomist/manifest.yml");
        if (manifestFile.isDefined()) {
            
            Manifest manifest = new Manifest();
            
            Iterable<Object> documents = readYaml(manifestFile);

            documents.forEach(doc -> {
                Map<String, Object> manifestYaml = (Map<String, Object>) doc;
                
                String group = (String) manifestYaml.get("group");
                String artifact = (String) manifestYaml.get("artifact");
                String version = (String) manifestYaml.get("version");
                String requires = (String) manifestYaml.get("requires");

                String repo = (String) manifestYaml.get("repo");
                String branch = (String) manifestYaml.get("branch");
                Object sha = manifestYaml.get("sha");
                
                if (group != null) {
                    manifest.setGroup(group);
                }
                if (artifact != null) {
                    manifest.setArtifact(artifact);
                }
                if (version != null) {
                    manifest.setVersion(version);
                }
                if (requires != null) {
                    manifest.setRequires(requires);
                }
                if (repo != null) {
                    manifest.setRepo(repo);
                }
                if (branch != null) {
                    manifest.setBranch(branch);
                }
                if (sha != null) {
                    manifest.setSha(sha.toString());
                }
                
                List<String> dependencies = (List<String>) manifestYaml.getOrDefault("dependencies",
                        Collections.emptyList());
                List<String> extensions = (List<String>) manifestYaml.getOrDefault("extensions",
                        Collections.emptyList());
                
                Map<String, Map<String, String>> repositories = (Map<String, Map<String, String>>) manifestYaml
                        .getOrDefault("repositories", Collections.emptyMap());
                
                if (dependencies != null) {
                    dependencies.forEach(d -> manifest.addDependency(Gav.formString(d)));
                }
                if (extensions != null) {
                    extensions.forEach(e -> manifest.addExtension(Gav.formString(e)));
                }
                if (repositories != null) {
                    repositories.entrySet().forEach(r -> manifest
                            .addRepository(new Repository(r.getKey(), r.getValue().get("url"))));
                }
            });
            

            return ManifestValidator.validate(manifest);
        }
        throw new ManifestException("manifest.yml could not be found in .atomist");
    }
    
    private Iterable<Object> readYaml(Option<FileArtifact> manifestFile) {
        try {
            Yaml yaml = new Yaml();
            return (Iterable<Object>) yaml.loadAll(manifestFile.get().content());
        }
        catch (ParserException e) {
            throw new ManifestException("manifest.yml file in .atomist is malformed:\n\n%s", e.getMessage());
        }
    }
}
