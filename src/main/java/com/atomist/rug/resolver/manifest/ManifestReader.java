package com.atomist.rug.resolver.manifest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;

import com.atomist.rug.resolver.project.GitInfo;
import com.atomist.rug.resolver.project.ProvenanceInfoArtifactSourceReader;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;

import scala.Option;

class ManifestReader {

    @SuppressWarnings("unchecked")
    public void read(ArtifactSource source, Manifest manifest) {
        Option<FileArtifact> manifestFile = source.findFile(".atomist/manifest.yml");
        if (manifestFile.isDefined()) {

            Iterable<Object> documents = readYaml(manifestFile);

            documents.forEach(doc -> {
                Map<String, Object> manifestYaml = (Map<String, Object>) doc;

                String group = (String) manifestYaml.get("group");
                String artifact = (String) manifestYaml.get("artifact");
                String version = (String) manifestYaml.get("version");
                String requires = (String) manifestYaml.get("requires");

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

                List<Object> dependencies = (List<Object>) manifestYaml.getOrDefault("dependencies",
                        Collections.emptyList());
                List<Object> extensions = (List<Object>) manifestYaml.getOrDefault("extensions",
                        Collections.emptyList());
                Map<String, Object> excludes = (Map<String, Object>) manifestYaml
                        .getOrDefault("excludes", Collections.emptyMap());

                if (dependencies != null) {
                    dependencies.forEach(d -> {
                        if (d instanceof String) {
                            manifest.addDependency(Gav.fromString((String) d));
                        }
                        else if (d instanceof Map) {
                            ((Map<String, String>) d).entrySet()
                                    .forEach(e -> manifest.addDependency(
                                            Gav.fromString(e.getKey() + ":" + e.getValue())));
                        }
                    });
                }
                if (extensions != null) {
                    extensions.forEach(e -> {
                        if (e instanceof String) {
                            manifest.addExtension(Gav.fromString((String) e));
                        }
                        else if (e instanceof Map) {
                            ((Map<String, String>) e).entrySet()
                                    .forEach(me -> manifest.addExtension(
                                            Gav.fromString(me.getKey() + ":" + me.getValue())));
                        }
                    });
                }

                if (excludes != null && !excludes.isEmpty()) {
                    Excludes ex = new Excludes();
                    excludes.entrySet().forEach(e -> {
                        List<String> values = (List<String>) e.getValue();
                        if (values == null) {
                            values = Collections.emptyList();
                        }
                        switch (e.getKey()) {
                        case "editors":
                            values.forEach(v -> ex.addEditor(v));
                            break;
                        case "generators":
                            values.forEach(v -> ex.addGenerator(v));
                            break;
                        case "command_handlers":
                            values.forEach(v -> ex.addCommandHandler(v));
                            break;
                        case "event_handlers":
                            values.forEach(v -> ex.addEventHandler(v));
                            break;
                        case "response_handlers":
                            values.forEach(v -> ex.addResponseHandler(v));
                            break;
                        default:
                        }
                    });
                    manifest.setExcludes(ex);
                }
            });

            Optional<GitInfo> provenanceInfoOptional = ProvenanceInfoArtifactSourceReader
                    .read(source);
            if (provenanceInfoOptional.isPresent()) {
                GitInfo provenanceInfo = provenanceInfoOptional.get();
                manifest.setRepo(provenanceInfo.repo());
                manifest.setBranch(provenanceInfo.branch());
                manifest.setSha(provenanceInfo.sha());
            }
        }
    }

    private Iterable<Object> readYaml(Option<FileArtifact> manifestFile) {
        try {
            Yaml yaml = new Yaml();
            return (Iterable<Object>) yaml.loadAll(manifestFile.get().content());
        }
        catch (ParserException e) {
            throw new ManifestParsingException("manifest.yml file in .atomist is malformed:\n\n%s",
                    e.getMessage());
        }
    }
}
