package com.atomist.rug.resolver;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;

public class DependencyCollectionException extends DependencyResolverException {

    private static final String LAST_TREE_NODE = (SystemUtils.IS_OS_WINDOWS ? "\\- " : "└── ");
    private static final String LAST_TREE_NODE_WITH_CHILDREN = (SystemUtils.IS_OS_WINDOWS ? "\\- "
            : "└─┬ ");
    private static final String REDIVID = (SystemUtils.IS_OS_WINDOWS ? "<" : "←");

    private static final long serialVersionUID = -5843449884566599662L;

    public DependencyCollectionException(
            org.eclipse.aether.collection.DependencyCollectionException e) {
        super(getSource(e.getMessage(), e.getResult()),
                e.getResult().getRequest().getRepositories(), e);
    }

    private static String getSource(String message, CollectResult result) {
        if (message.startsWith("Failed to collect dependencies at ")) {

            StringBuilder sb = new StringBuilder();
            sb.append("Failed to resolve and download dependencies:");

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
                return sb.toString();
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
                return String.format("Could not find requested archive %s:%s:%s:%s.",
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                        artifact.getVersion());
            }
        }
        return message;
    }
}
