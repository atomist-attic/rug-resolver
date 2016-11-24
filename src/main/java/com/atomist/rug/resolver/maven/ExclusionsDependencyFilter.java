package com.atomist.rug.resolver.maven;

import java.util.List;
import java.util.Optional;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

public class ExclusionsDependencyFilter implements DependencyFilter {

    private List<String> exclusions = null;
    private PathMatcher matcher = new AntPathMatcher(":");

    public ExclusionsDependencyFilter(List<String> exclusions) {
        this.exclusions = exclusions;
    }

    @Override
    public boolean accept(DependencyNode node, List<DependencyNode> parents) {
        Artifact artifact = node.getArtifact();
        String gav = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                + artifact.getExtension() + ":" + artifact.getVersion();
        Optional<String> exclusionMatch = exclusions.stream().filter(e -> matcher.match(e, gav))
                .findAny();
        return !exclusionMatch.isPresent();
    }

}
