package com.atomist.rug.resolver.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.ArtifactDescriptorFactory;
import com.atomist.rug.resolver.DefaultArtifactDescriptor;
import com.atomist.rug.resolver.DependencyResolver;
import com.atomist.rug.resolver.DependencyResolverException;

import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;

@Component
public class MavenBasedDependencyResolver implements DependencyResolver {

    private static final Logger logger = LoggerFactory
            .getLogger(MavenBasedDependencyResolver.class);

    private final MavenProperties properties;
    private final RepositorySystem repoSystem;
    private final ExecutorService executorService;
    private TransferListener transferListener;
    private ProxySelector proxySelector;
    private List<String> exclusions = new ArrayList<>();
    private List<DependencyVisitor> additionalVisitors = new ArrayList<>();

    @Autowired
    public MavenBasedDependencyResolver(RepositorySystem repoSystem, MavenProperties properties,
            @Qualifier("maven-resolver-pool") ExecutorService executorService) {
        this.repoSystem = repoSystem;
        this.properties = properties;
        this.exclusions = properties.getExclusions();
        this.executorService = executorService;
        this.transferListener = new LogTransferListener();
    }

    public void addDependencyVisitor(DependencyVisitor visitor) {
        this.additionalVisitors.add(visitor);
    }

    public void setExclusions(List<String> exclusions) {
        this.exclusions = exclusions;
    }

    public void setTransferListener(TransferListener transferListener) {
        this.transferListener = transferListener;
    }

    public void setProxySelector(ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
    }

    @Override
    public String resolveVersion(ArtifactDescriptor artifact) throws DependencyResolverException {
        RepositorySystemSession session = newSession(repoSystem, createDependencyRoot(artifact));
        List<RemoteRepository> remotes = properties.repositories();
        return getVersion(artifact, session, remotes);
    }

    @Override
    public List<ArtifactDescriptor> resolveTransitiveDependencies(ArtifactDescriptor artifact)
            throws DependencyResolverException {

        if (logger.isInfoEnabled()) {
            logger.info(String.format("Resolving dependencies for %s:%s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.extension().toString().toLowerCase(),
                    artifact.version()));
        }

        RepositorySystemSession session = newSession(repoSystem, createDependencyRoot(artifact));
        List<RemoteRepository> remotes = properties.repositories();

        List<DependencyNode> dependencies = collectDependencies(artifact, session, remotes);

        List<FutureTask<ArtifactDescriptor>> resolveFutures = new ArrayList<>();

        for (DependencyNode node : dependencies) {

            FutureTask<ArtifactDescriptor> resolveFuture = new FutureTask<>(() -> {
                Artifact dependency = node.getArtifact();
                if (dependency.getFile() == null) {
                    try {
                        ArtifactResult result = repoSystem.resolveArtifact(session,
                                new ArtifactRequest(node));
                        dependency = result.getArtifact();
                    }
                    catch (ArtifactResolutionException e) {
                        logger.warn(
                                String.format(
                                        "Failed to resolveTransitiveDependencies rug archive for %s:%s:%s",
                                        artifact.group(), artifact.artifact(), artifact.version()),
                                e);
                        throw new DependencyResolverException(
                                String.format(
                                        "Failed to resolveTransitiveDependencies rug archive for %s:%s:%s",
                                        artifact.group(), artifact.artifact(), artifact.version()),
                                e);

                    }
                }
                return new DefaultArtifactDescriptor(dependency.getGroupId(),
                        dependency.getArtifactId(), dependency.getBaseVersion(),
                        ArtifactDescriptorFactory.toExtension(dependency.getExtension()),
                        ArtifactDescriptorFactory.toScope(node.getDependency().getScope()),
                        dependency.getFile().toURI());
            });

            resolveFutures.add(resolveFuture);
            executorService.submit(resolveFuture);

        }

        return resolveFutures.stream().map(rt -> {
            try {
                return rt.get();
            }
            catch (InterruptedException e) {
                throw new DependencyResolverException("Interrupt exception occurred", e);
            }
            catch (ExecutionException e) {
                if (e.getCause() instanceof DependencyResolverException) {
                    throw (DependencyResolverException) e.getCause();
                }
                else {
                    throw new DependencyResolverException(e.getMessage(), e);
                }
            }
        }).collect(Collectors.toList());

    }

    @Override
    public List<ArtifactDescriptor> resolveDirectDependencies(ArtifactDescriptor artifactDescriptor)
            throws DependencyResolverException {

        RepositorySystemSession session = newSession(repoSystem,
                createDependencyRoot(artifactDescriptor));
        List<RemoteRepository> remotes = properties.repositories();

        Artifact artifact = new DefaultArtifact(
                String.format("%s:%s:%s", artifactDescriptor.group(), artifactDescriptor.artifact(),
                        artifactDescriptor.version()));

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        descriptorRequest.setRepositories(remotes);

        try {
            return repoSystem.readArtifactDescriptor(session, descriptorRequest).getDependencies()
                    .stream()
                    .map(d -> new DefaultArtifactDescriptor(d.getArtifact().getGroupId(),
                            d.getArtifact().getArtifactId(), d.getArtifact().getVersion(),
                            ArtifactDescriptorFactory.toExtension(d.getArtifact().getExtension()),
                            ArtifactDescriptorFactory.toScope(d.getScope()), null))
                    .collect(Collectors.toList());
        }
        catch (ArtifactDescriptorException e) {
            throw new DependencyResolverException(String.format(
                    "Failed to collect dependencies for %s:%s:%s", artifactDescriptor.group(),
                    artifactDescriptor.artifact(), artifactDescriptor.version()), e);
        }
    }

    protected Dependency createDependencyRoot(ArtifactDescriptor artifactDescriptor) {
        Artifact artifact = new DefaultArtifact(String.format("%s:%s:%s:%s",
                artifactDescriptor.group(), artifactDescriptor.artifact(),
                artifactDescriptor.extension().toString().toLowerCase(),
                artifactDescriptor.version()));
        return new Dependency(artifact, "compile");
    }

    private List<DependencyNode> collectDependencies(ArtifactDescriptor artifact,
            RepositorySystemSession session, List<RemoteRepository> remotes)
            throws DependencyResolverException {

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(createDependencyRoot(artifact));
        collectRequest.setRepositories(remotes);
        artifact.dependencies()
                .forEach(ad -> collectRequest.addDependency(createDependencyRoot(ad)));

        List<DependencyNode> artifacts = new ArrayList<>();

        try {
            CollectResult collectResult = repoSystem.collectDependencies(session, collectRequest);

            logger.info("Dependencies for {}:{}:{} resolved:", artifact.group(),
                    artifact.artifact(), artifact.version());
            collectResult.getRoot().accept(new TreeDependencyVisitor(new FilteringDependencyVisitor(
                    new LogDependencyVisitor(new LogDependencyVisitor.Log() {

                        @Override
                        public void info(String message) {
                            logger.info(message);
                        }

                    }, new DependencyVisitor() {

                        public boolean visitEnter(DependencyNode node) {
                            return true;
                        }

                        public boolean visitLeave(DependencyNode node) {
                            artifacts.add(node);
                            return true;
                        }
                    }),
                    new ExclusionsDependencyFilter(MavenBasedDependencyResolver.this.exclusions))));

            additionalVisitors.forEach(a -> collectResult.getRoot().accept(a));

        }
        catch (DependencyCollectionException e) {
            throw new com.atomist.rug.resolver.maven.DependencyCollectionException(e);
        }

        return artifacts;
    }

    private String getLatestVersion(String groupId, String artifactId,
            RepositorySystemSession session, List<RemoteRepository> remotes)
            throws VersionRangeResolutionException {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(
                new DefaultArtifact(String.format("%s:%s:%s", groupId, artifactId, "[,)")));
        rangeRequest.setRepositories(remotes);

        VersionRangeResult rangeResult = repoSystem.resolveVersionRange(session, rangeRequest);
        if (rangeResult.getHighestVersion() != null) {
            return rangeResult.getHighestVersion().toString();
        }
        else {
            throw new com.atomist.rug.resolver.maven.DependencyCollectionException(String
                    .format("Unable to find a version of archive %s:%s.", groupId, artifactId),
                    properties.repositories());
        }
    }

    private String getVersion(ArtifactDescriptor artifact, RepositorySystemSession session,
            List<RemoteRepository> remotes) throws DependencyResolverException {
        String version = artifact.version();
        if ("latest".equals(version)) {
            try {
                version = getLatestVersion(artifact.group(), artifact.artifact(), session, remotes);
            }
            catch (VersionRangeResolutionException e) {
                throw new DependencyResolverException(
                        String.format("Failed to calculate latest version for %s:%s",
                                artifact.group(), artifact.artifact()),
                        e);
            }
        }
        return version;
    }

    private RepositorySystemSession newSession(RepositorySystem system, Dependency root)
            throws DependencyResolverException {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        // Use special LocalRepositoryManager to enable concurrent use
        try {
            LocalRepository localRepo = new LocalRepository(properties.getRepoLocation());
            session.setLocalRepositoryManager(
                    new TakariLocalRepositoryManagerFactory().newInstance(session, localRepo));
        }
        catch (NoLocalRepositoryManagerException e) {
            throw new DependencyResolverException("Error initiating repository session", e);
        }
        session.setAuthenticationSelector(repository -> {
            Optional<MavenProperties.Repo> repo = properties.getPomRepos().stream()
                    .filter(r -> repository.getUrl().startsWith(r.getUrl())).findAny();
            if (repo.isPresent()) {
                return repo.get().getAuth().authentication();
            }
            return null;
        });

        List<Exclusion> exclusions = this.exclusions.stream().map(e -> {
            String[] parts = e.split(":");
            return new Exclusion(parts[0], parts[1], "", "jar");
        }).collect(Collectors.toList());

        DependencySelector depFilter = new AndDependencySelector(
                new ScopeDependencySelector("test", "provided"), new OptionalDependencySelector(),
                new ExclusionDependencySelector(exclusions));

        session.setDependencySelector(depFilter);
        if (!properties.isCacheMetadata()) {
            session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        }

        if (transferListener != null) {
            session.setTransferListener(transferListener);
        }
        if (proxySelector != null) {
            session.setProxySelector(proxySelector);
        }
        session.setOffline(properties.isOffline());

        // Needed for proper normalization of snapshot versions
        session.setConfigProperty("aether.artifactResolver.snapshotNormalization", true);

        return session;
    }
}
