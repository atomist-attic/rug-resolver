package com.atomist.rug.resolver;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

public class UriBasedDependencyResolver implements DependencyResolver {

        private List<ArtifactDescriptor> descriptors;

        public UriBasedDependencyResolver(URI[] uris, String repositoryLocation) {

            URI repoHome = new File(repositoryLocation).toURI();

            descriptors = Arrays.asList(uris).stream().map(u -> {
                URI relativeUri = repoHome.relativize(u);
                List<String> segments = new ArrayList<>(
                        Arrays.asList(relativeUri.toString().split("/")));
                // last segment is the actual file name
                segments.remove(segments.size() - 1);
                // last segments is version
                String version = segments.remove(segments.size() - 1);
                // second to last is artifact
                String artifact = segments.remove(segments.size() - 1);
                // remaining segments are group
                String group = StringUtils.collectionToDelimitedString(segments, ".");
                return new DefaultArtifactDescriptor(group, artifact, version,
                        ArtifactDescriptor.Extension.ZIP, ArtifactDescriptor.Scope.COMPILE, u);
            }).collect(Collectors.toList());
        }

        @Override
        public List<ArtifactDescriptor> resolveDirectDependencies(ArtifactDescriptor artifact)
                throws DependencyResolverException {
            return descriptors;
        }

        @Override
        public List<ArtifactDescriptor> resolveTransitiveDependencies(ArtifactDescriptor artifact)
                throws DependencyResolverException {
            return descriptors;
        }

        @Override
        public String resolveVersion(ArtifactDescriptor artifact)
                throws DependencyResolverException {
            return artifact.version();
        }
    }