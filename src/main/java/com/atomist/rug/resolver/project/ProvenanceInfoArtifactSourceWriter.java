package com.atomist.rug.resolver.project;

import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.FileEditor;
import com.atomist.source.StringFileArtifact;

import scala.Option;

public abstract class ProvenanceInfoArtifactSourceWriter {

    public static ArtifactSource write(GitInfo provenanceInfo, ArtifactSource source) {
        if (provenanceInfo == null) {
            return source;
        }

        String provenance = writeProvenanceInfoToManifest(provenanceInfo, source);

        Option<FileArtifact> manifestArtifact = source.findFile(".atomist/manifest.yml");
        if (manifestArtifact.isDefined()) {
            StringBuilder sb = new StringBuilder(manifestArtifact.get().content());
            FileArtifact newManifest = StringFileArtifact.apply("manifest.yml", ".atomist",
                    sb.append(provenance).toString());

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
        else {
            FileArtifact newManifest = StringFileArtifact.apply("manifest.yml", ".atomist",
                    provenance);
            return source.plus(newManifest);
        }
    }

    private static String writeProvenanceInfoToManifest(GitInfo provenanceInfo,
            ArtifactSource source) {
        StringBuilder sb = new StringBuilder().append("\n---\n");
        sb.append("repo: \"").append(provenanceInfo.repo()).append("\"\n");
        sb.append("branch: \"").append(provenanceInfo.branch()).append("\"\n");
        sb.append("sha: \"").append(provenanceInfo.sha()).append("\"\n");
        return sb.toString();
    }

}
