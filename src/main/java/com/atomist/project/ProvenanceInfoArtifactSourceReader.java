package com.atomist.project;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.yaml.snakeyaml.Yaml;

import com.atomist.rug.manifest.Manifest;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.fasterxml.jackson.databind.ObjectMapper;

import scala.Option;

public class ProvenanceInfoArtifactSourceReader {

    @SuppressWarnings("unchecked")
    public Optional<ProvenanceInfo> read(ArtifactSource source) {
        String repo = null;
        String branch = null;
        String sha = null;

        Option<FileArtifact> manifest = source
                .findFile(".atomist/" + Manifest.FILE_NAME);
        Option<FileArtifact> packageJson = source
                .findFile(".atomist/package.json");

        if (manifest.isDefined()) {
            Yaml yaml = new Yaml();
            Map<String, Object> allDocuments = new HashMap<>();
            Iterable<?> iterator = yaml.loadAll(manifest.get().content());
            iterator.forEach(d -> {
                Map<String, Object> document = (Map<String, Object>) d;
                allDocuments.putAll(document);
            });
            if (allDocuments.containsKey("repo")) {
                repo = (String) allDocuments.get("repo");
            }
            if (allDocuments.containsKey("branch")) {
                branch = (String) allDocuments.get("branch");
            }
            if (allDocuments.containsKey("sha")) {
                sha = (String) allDocuments.get("sha");
            }
        }
        else if (packageJson.isDefined()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> allDocuments = mapper.readValue(packageJson.get().content(),
                        Map.class);
                if (allDocuments.containsKey("atomist")) {
                    Map<String, Object> atomist = (Map<String, Object>) allDocuments.get("atomist");
                    if (atomist.containsKey("repo")) {
                        repo = (String) atomist.get("repo");
                    }
                    if (atomist.containsKey("branch")) {
                        branch = (String) atomist.get("branch");
                    }
                    if (atomist.containsKey("sha")) {
                        sha = (String) atomist.get("sha");
                    }
                    if (repo != null && branch != null && sha != null) {
                        return Optional.of(new SimpleProvenanceInfo(repo, branch, sha));
                    }
                }
            }
            catch (IOException e) {
            }
        }

        if (repo != null && branch != null && sha != null) {
            return Optional.of(new SimpleProvenanceInfo(repo, branch, sha));
        }
        else {
            return Optional.empty();
        }
    }

}
