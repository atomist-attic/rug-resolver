package com.atomist.project;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.FileEditor;
import com.atomist.source.StringFileArtifact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import scala.Option;

public class ProvenanceInfoArtifactSourceWriter {

    public ArtifactSource write(ProvenanceInfo provenanceInfo, ArtifactSource source) {
        if (provenanceInfo == null) {
            return source;
        }

        Option<FileArtifact> manifestArtifact = source.findFile(".atomist/manifest.yml");
        Option<FileArtifact> packageJsonArtifact = source.findFile(".atomist/package.json");
        if (manifestArtifact.isDefined()) {
            return writeProvenanceInfoToManifest(provenanceInfo, source, manifestArtifact);
        }
        else if (packageJsonArtifact.isDefined()) {
            return writeProvenanceInfoToPackageJson(provenanceInfo, source, packageJsonArtifact);
        }
        return source;
    }

    private ArtifactSource writeProvenanceInfoToManifest(ProvenanceInfo provenanceInfo,
            ArtifactSource source, Option<FileArtifact> manifestArtifact) {
        StringBuilder sb = new StringBuilder(manifestArtifact.get().content()).append("\n---\n");
        sb.append("repo: \"").append(provenanceInfo.repo().get()).append("\"\n");
        sb.append("branch: \"").append(provenanceInfo.branch().get()).append("\"\n");
        sb.append("sha: \"").append(provenanceInfo.sha().get()).append("\"\n");
        FileArtifact newManifest = new StringFileArtifact("manifest.yml", ".atomist",
                sb.toString());

        return source.edit(new FileEditor() {
            @Override
            public boolean canAffect(FileArtifact f) {
                return f.path().equals(newManifest.path());
            }

            @Override
            public FileArtifact edit(FileArtifact f) {
                return newManifest;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private ArtifactSource writeProvenanceInfoToPackageJson(ProvenanceInfo provenanceInfo,
            ArtifactSource source, Option<FileArtifact> packageJsonArtifact) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Map<String, Object> packageJson = mapper.readValue(packageJsonArtifact.get().content(),
                    Map.class);
            Map<String, Object> atomist = (Map<String, Object>) packageJson.getOrDefault("atomist",
                    new HashMap<String, Object>());

            atomist.put("repo", provenanceInfo.repo().get());
            atomist.put("branch", provenanceInfo.branch().get());
            atomist.put("sha", provenanceInfo.sha().get());

            packageJson.put("atomist", atomist);

            FileArtifact newManifest = new StringFileArtifact("package.json", ".atomist",
                    mapper.writeValueAsString(packageJson));

            return source.edit(new FileEditor() {
                @Override
                public boolean canAffect(FileArtifact f) {
                    return f.path().equals(newManifest.path());
                }

                @Override
                public FileArtifact edit(FileArtifact f) {
                    return newManifest;
                }
            });
        }
        catch (IOException e) {
            return source;
        }
    }
}
