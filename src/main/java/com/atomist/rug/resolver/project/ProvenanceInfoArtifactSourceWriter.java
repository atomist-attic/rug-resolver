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

        Option<FileArtifact> manifestArtifact = source.findFile(".atomist/manifest.yml");
        if (manifestArtifact.isDefined()) {
            return writeProvenanceInfoToManifest(provenanceInfo, source, manifestArtifact);
        }
        return source;
    }

    private static ArtifactSource writeProvenanceInfoToManifest(GitInfo provenanceInfo,
            ArtifactSource source, Option<FileArtifact> manifestArtifact) {
        StringBuilder sb = new StringBuilder(manifestArtifact.get().content()).append("\n---\n");
        sb.append("repo: \"").append(provenanceInfo.repo()).append("\"\n");
        sb.append("branch: \"").append(provenanceInfo.branch()).append("\"\n");
        sb.append("sha: \"").append(provenanceInfo.sha()).append("\"\n");
        FileArtifact newManifest = StringFileArtifact.apply("manifest.yml", ".atomist",
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

}
