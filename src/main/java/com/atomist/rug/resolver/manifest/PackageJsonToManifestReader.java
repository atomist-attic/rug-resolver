package com.atomist.rug.resolver.manifest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.fasterxml.jackson.databind.ObjectMapper;

import scala.Option;

class PackageJsonToManifestReader {

    private static final List<String> METADATA_KEYS = Arrays
            .asList(new String[] { "license", "description", "homepage", "keywords", "bugs",
                    "author", "contributors", "repository" });
    private static final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public void read(ArtifactSource source, Manifest manifest) {
        Option<FileArtifact> packageJsonFile = source.findFile(".atomist/package.json");
        if (packageJsonFile.isDefined()) {

            Map<String, Object> pJson = readPackageJson(packageJsonFile.get().content());

            String artifact = (String) pJson.get("name");
            // Deal with scoped packages
            if (artifact != null && artifact.startsWith("@")) {
                manifest.setGroup(artifact.substring(1, artifact.indexOf('/')));
                manifest.setArtifact(artifact.substring(artifact.indexOf('/') + 1));
            }
            else {
                manifest.setGroup((String) pJson.get("group"));
                manifest.setArtifact((String) pJson.get("name"));
            }
            if (manifest.group() == null) {
                manifest.setGroup(manifest.artifact());
            }

            manifest.setVersion((String) pJson.get("version"));

            Map<String, Object> atomist = (Map<String, Object>) pJson.getOrDefault("atomist",
                    pJson.getOrDefault("com_atomist", Collections.emptyMap()));
            if (atomist.containsKey("requires")) {
                manifest.setRequires(ManifestUtils.parseVersion((String) atomist.get("requires")));
            }

            Map<String, String> dependencies = (Map<String, String>) atomist
                    .getOrDefault("dependencies", Collections.emptyMap());
            dependencies.entrySet().stream().forEach(e -> manifest.addDependency(Gav.fromEntry(e)));

            Map<String, String> extensions = (Map<String, String>) atomist
                    .getOrDefault("extensions", Collections.emptyMap());
            extensions.entrySet().stream().forEach(e -> manifest.addExtension(Gav.fromEntry(e)));

            Map<String, Object> excludes = (Map<String, Object>) atomist.getOrDefault("excludes",
                    Collections.emptyMap());

            if (!excludes.isEmpty()) {
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
                    case "command-handlers":
                        values.forEach(v -> ex.addCommandHandler(v));
                        break;
                    case "event-handlers":
                        values.forEach(v -> ex.addEventHandler(v));
                        break;
                    case "response-handlers":
                        values.forEach(v -> ex.addResponseHandler(v));
                        break;
                    default:
                    }
                });
                manifest.setExcludes(ex);
            }

            METADATA_KEYS.stream().filter(k -> pJson.containsKey(k))
                    .forEach(k -> manifest.addMetadata(k, pJson.get(k)));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPackageJson(String contents) {
        try {
            return mapper.readValue(contents, Map.class);
        }
        catch (IOException e) {
            throw new ManifestParsingException("Error reading package.json", e);
        }
    }
}