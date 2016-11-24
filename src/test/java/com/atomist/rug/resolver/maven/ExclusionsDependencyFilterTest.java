package com.atomist.rug.resolver.maven;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Test;

public class ExclusionsDependencyFilterTest {

    @Test
    public void testWildcardPattern() {
        DependencyFilter filter = new ExclusionsDependencyFilter(
                Collections.singletonList("*:*:jar:*"));

        DependencyNode node1 = new DefaultDependencyNode(
                new DefaultArtifact("com.atomist", "blabla", "jar", "1.0.0"));
        DependencyNode node2 = new DefaultDependencyNode(
                new DefaultArtifact("com.atomist", "blabla", "zip", "1.0.0"));

        assertFalse(filter.accept(node1, null));
        assertTrue(filter.accept(node2, null));

    }
}
