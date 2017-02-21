package com.atomist.rug.resolver.maven;

import com.atomist.rug.resolver.DependencyResolverException;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

public class DependencyCollectionException extends DependencyResolverException {

    private static final String LAST_TREE_NODE = (SystemUtils.IS_OS_WINDOWS ? "\\- " : "└── ");
    private static final String LAST_TREE_NODE_WITH_CHILDREN = (SystemUtils.IS_OS_WINDOWS ? "\\- "
            : "└─┬ ");
    private static final String REDIVID = (SystemUtils.IS_OS_WINDOWS ? "<" : "←");

    private static final long serialVersionUID = -5843449884566599662L;

    private List<RemoteRepository> remoteRepositories = Collections.emptyList();
    private String detailMessage = null;
    private ErrorType type = null;

    public DependencyCollectionException(
            org.eclipse.aether.collection.DependencyCollectionException e) {
        super(e.getMessage(), e);
        this.remoteRepositories = e.getResult().getRequest().getRepositories();
        getSource(e.getMessage(), e.getResult());
    }

    public DependencyCollectionException(ArtifactResolutionException e) {
        super(e.getMessage());
        this.remoteRepositories = e.getResult().getRequest().getRepositories();
        getSource(e.getMessage(), e.getResult());
    }

    public DependencyCollectionException(String message,
            List<RemoteRepository> remoteRepositories) {
        super(message);
        this.remoteRepositories = remoteRepositories;
    }

    private void getSource(String message, ArtifactResult result) {
        if (message.startsWith("Could not find artifact ")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Failed to resolve or download dependencies:");
            sb.append("\n").append("  ").append(result.getRequest().getArtifact());
            sb.append(" ").append(REDIVID).append(" missing or unable to download");
            this.type = ErrorType.DEPENDENCY_ERROR;
            this.detailMessage = sb.toString();
        }
    }

    private void getSource(String message, CollectResult result) {
        if (message.startsWith("Failed to collect dependencies at ")) {

            StringBuilder sb = new StringBuilder();
            sb.append("Failed to resolve or download dependencies:");

            message = message.substring("Failed to collect dependencies at ".length());
            String[] artifacts = message.split(" -> ");
            if (artifacts != null && artifacts.length > 0) {
                String indent = "  ";
                sb.append("\n").append(indent).append(artifacts[0]);
                for (int i = 1; i < artifacts.length; i++) {
                    sb.append("\n").append(indent);
                    if (i + 1 == artifacts.length) {
                        sb.append(LAST_TREE_NODE);
                        sb.append(artifacts[i]);
                        sb.append(" ").append(REDIVID).append(" missing or unable to download");
                    }
                    else {
                        sb.append(LAST_TREE_NODE_WITH_CHILDREN);
                        sb.append(artifacts[i]);
                    }
                    indent = indent + "  ";
                }
                this.type = ErrorType.DEPENDENCY_ERROR;
                this.detailMessage = sb.toString();
            }
        }
        else if (message.startsWith("Failed to read artifact descriptor for ")) {
            CollectRequest request = result.getRequest();
            Artifact artifact = null;
            if (request.getRoot() != null) {
                artifact = request.getRoot().getArtifact();
            }
            if (request.getRootArtifact() != null) {
                artifact = request.getRootArtifact();
            }
            if (artifact != null) {
                this.type = ErrorType.ROOT_ARTIFACT_ERROR;
                this.detailMessage = String.format("Could not find requested archive %s:%s:%s:%s.",
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                        artifact.getVersion());
            }
        }
    }

    public ErrorType getType() {
        return type;
    }

    public String getDetailMessage() {
        return detailMessage;
    }

    public List<RemoteRepository> getRemoteRepositories() {
        return remoteRepositories;
    }

    public enum ErrorType {
        DEPENDENCY_ERROR, ROOT_ARTIFACT_ERROR
    }
}
