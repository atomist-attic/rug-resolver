package com.atomist.rug.resolver.maven;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
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
import com.atomist.rug.resolver.ArtifactDescriptor.Scope;
import com.atomist.rug.resolver.ArtifactDescriptorFactory;
import com.atomist.rug.resolver.DefaultArtifactDescriptor;
import com.atomist.rug.resolver.DependencyResolver;
import com.atomist.rug.resolver.DependencyResolverException;
import com.atomist.rug.resolver.DependencyVerificationFailedException;
import com.atomist.rug.resolver.DependencyVerifier;

import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;

@Component
public class MavenBasedDependencyResolver implements DependencyResolver {

    private static final Logger logger = LoggerFactory
            .getLogger(MavenBasedDependencyResolver.class);

    private List<DependencyVisitor> additionalVisitors = new ArrayList<>();
    private List<String> exclusions = new ArrayList<>();
    private final ExecutorService executorService;
    private final MavenProperties properties;
    private ProxySelector proxySelector;
    private final RepositorySystem repoSystem;
    private TransferListener transferListener;

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

    @Override
    public ArtifactDescriptor resolveRugs(ArtifactDescriptor artifact)
            throws DependencyResolverException {
        if (logger.isInfoEnabled()) {
            logger.info(String.format("Resolving rugs for %s:%s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.extension().toString().toLowerCase(),
                    artifact.version()));
        }

        RepositorySystemSession session = newSession(repoSystem, createDependencyRoot(artifact),
                false, "*:*");
        List<RemoteRepository> remotes = properties.repositories();

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(createDependencyRoot(artifact));
        collectRequest.setRepositories(remotes);
        artifact.dependencies()
                .forEach(ad -> collectRequest.addDependency(createDependencyRoot(ad)));

        List<DependencyNode> artifacts = new ArrayList<>();
        DependencyNode root = null;

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
            root = collectResult.getRoot();

        }
        catch (DependencyCollectionException e) {
            throw new com.atomist.rug.resolver.maven.DependencyCollectionException(e);
        }

        List<FutureTask<ArtifactDescriptor>> resolveFutures = new ArrayList<>();
        Map<DependencyNode, String> resolvedURIs = new ConcurrentHashMap<>();

        for (DependencyNode node : artifacts) {

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
                                String.format("Failed to resolve rug archive for %s:%s:%s",
                                        artifact.group(), artifact.artifact(), artifact.version()),
                                e);
                        throw new com.atomist.rug.resolver.maven.DependencyCollectionException(e);

                    }
                }
                resolvedURIs.put(node, dependency.getFile().getAbsolutePath());
                return new DefaultArtifactDescriptor(dependency.getGroupId(),
                        dependency.getArtifactId(), dependency.getBaseVersion(),
                        ArtifactDescriptorFactory.toExtension(dependency.getExtension()),
                        ArtifactDescriptorFactory.toScope(node.getDependency().getScope()),
                        dependency.getFile().getAbsolutePath());
            });

            resolveFutures.add(resolveFuture);
            executorService.submit(resolveFuture);

        }
        resolveFutures.stream().forEach(rt -> {
            try {
                rt.get();
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
        });

        return processNode(root, resolvedURIs);
    }

    private ArtifactDescriptor processNode(DependencyNode node,
            Map<DependencyNode, String> resolvedURIs) {
        Artifact dependency = node.getArtifact();
        DefaultArtifactDescriptor artifact = new DefaultArtifactDescriptor(dependency.getGroupId(),
                dependency.getArtifactId(), dependency.getBaseVersion(),
                ArtifactDescriptorFactory.toExtension(dependency.getExtension()),
                ArtifactDescriptorFactory.toScope(node.getDependency().getScope()),
                resolvedURIs.get(node));
        node.getChildren().forEach(d -> artifact.addDependency(processNode(d, resolvedURIs)));
        return artifact;
    }

    @Override
    public List<ArtifactDescriptor> resolveDependencies(ArtifactDescriptor artifact,
            DependencyVerifier... verifiers) throws DependencyResolverException {

        if (logger.isInfoEnabled()) {
            logger.info(String.format("Resolving dependencies for %s:%s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.extension().toString().toLowerCase(),
                    artifact.version()));
        }

        RepositorySystemSession session = newSession(repoSystem, createDependencyRoot(artifact),
                true);
        List<RemoteRepository> remotes = properties.repositories();

        List<DependencyNode> dependencies = collectDependencies(artifact, session, remotes,
                verifiers);

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
                                String.format("Failed to resolve rug archive for %s:%s:%s",
                                        artifact.group(), artifact.artifact(), artifact.version()),
                                e);
                        throw new com.atomist.rug.resolver.maven.DependencyCollectionException(e);

                    }
                }
                return new DefaultArtifactDescriptor(dependency.getGroupId(),
                        dependency.getArtifactId(), dependency.getBaseVersion(),
                        ArtifactDescriptorFactory.toExtension(dependency.getExtension()),
                        ArtifactDescriptorFactory.toScope(node.getDependency().getScope()),
                        dependency.getFile().getAbsolutePath());
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
    public String resolveVersion(ArtifactDescriptor artifact) throws DependencyResolverException {
        RepositorySystemSession session = newSession(repoSystem, createDependencyRoot(artifact),
                true);
        List<RemoteRepository> remotes = properties.repositories();
        return getVersion(artifact, session, remotes);
    }

    public void setExclusions(List<String> exclusions) {
        this.exclusions = exclusions;
    }

    public void setProxySelector(ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
    }

    public void setTransferListener(TransferListener transferListener) {
        this.transferListener = transferListener;
    }

    private List<DependencyNode> collectDependencies(ArtifactDescriptor artifact,
            RepositorySystemSession session, List<RemoteRepository> remotes,
            DependencyVerifier... verifiers) throws DependencyResolverException {

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

                        private Stack<DependencyNode> nodes = new Stack<>();

                        public boolean visitEnter(DependencyNode node) {
                            if (shouldVerify(node, (!nodes.isEmpty() ? nodes.peek() : null))) {
                                if (!verify(node, session, repoSystem, verifiers)) {
                                    throw new DependencyVerificationFailedException(
                                            String.format("Verification of %s:%s (%s) failed",
                                                    node.getArtifact().getGroupId(),
                                                    node.getArtifact().getArtifactId(),
                                                    node.getArtifact().getVersion()),
                                            node.getArtifact().getGroupId(),
                                            node.getArtifact().getArtifactId(),
                                            node.getArtifact().getVersion());
                                }
                            }
                            nodes.push(node);
                            return true;
                        }

                        public boolean visitLeave(DependencyNode node) {
                            artifacts.add(node);
                            nodes.pop();
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

    protected boolean shouldVerify(DependencyNode node, DependencyNode parent) {
        if (parent == null) {
            return false;
        }
        if (node.getArtifact().getExtension().equals("jar")
                && parent.getArtifact().getExtension().equals("zip")) {
            return true;
        }
        else {
            return false;
        }
    }

    private boolean verify(DependencyNode node, RepositorySystemSession session,
            RepositorySystem system, DependencyVerifier... verifiers) {
        List<DependencyVerifier> vs = Arrays.asList(verifiers);
        boolean result = true;
        if (vs.size() > 0) {
            try {
                Artifact na = node.getArtifact();
                vs.forEach(v -> v.prepare(na.getGroupId(), na.getArtifactId(), na.getVersion()));

                Artifact signatureArtifact = new DefaultArtifact(node.getArtifact().getGroupId(),
                        node.getArtifact().getArtifactId(), node.getArtifact().getClassifier(),
                        node.getArtifact().getExtension() + ".asc",
                        node.getArtifact().getVersion());
                Artifact pomArtifact = new DefaultArtifact(node.getArtifact().getGroupId(),
                        node.getArtifact().getArtifactId(), node.getArtifact().getClassifier(),
                        "pom", node.getArtifact().getVersion());
                Artifact pomSignatureArtifact = new DefaultArtifact(node.getArtifact().getGroupId(),
                        node.getArtifact().getArtifactId(), node.getArtifact().getClassifier(),
                        "pom.asc", node.getArtifact().getVersion());

                List<ArtifactResult> resolveResult = system.resolveArtifacts(session, Arrays.asList(
                        new ArtifactRequest(node),
                        new ArtifactRequest(signatureArtifact, node.getRepositories(), null),
                        new ArtifactRequest(pomArtifact, node.getRepositories(), null),
                        new ArtifactRequest(pomSignatureArtifact, node.getRepositories(), null)));

                Optional<DefaultArtifactDescriptor> jar = resolveResult.stream()
                        .filter(a -> a.getArtifact().getExtension().equals("jar"))
                        .map(a -> new DefaultArtifactDescriptor(a.getArtifact().getGroupId(),
                                a.getArtifact().getArtifactId(), a.getArtifact().getVersion(),
                                ArtifactDescriptorFactory
                                        .toExtension(a.getArtifact().getExtension()),
                                Scope.COMPILE, a.getArtifact().getClassifier(),
                                a.getArtifact().getFile().getAbsolutePath()))
                        .findFirst();
                Optional<DefaultArtifactDescriptor> asc = resolveResult.stream()
                        .filter(a -> a.getArtifact().getExtension().equals("jar.asc"))
                        .map(a -> new DefaultArtifactDescriptor(a.getArtifact().getGroupId(),
                                a.getArtifact().getArtifactId(), a.getArtifact().getVersion(),
                                ArtifactDescriptorFactory
                                        .toExtension(a.getArtifact().getExtension()),
                                Scope.COMPILE, a.getArtifact().getClassifier(),
                                a.getArtifact().getFile().getAbsolutePath()))
                        .findFirst();

                Optional<DefaultArtifactDescriptor> pom = resolveResult.stream()
                        .filter(a -> a.getArtifact().getExtension().equals("pom"))
                        .map(a -> new DefaultArtifactDescriptor(a.getArtifact().getGroupId(),
                                a.getArtifact().getArtifactId(), a.getArtifact().getVersion(),
                                ArtifactDescriptorFactory
                                        .toExtension(a.getArtifact().getExtension()),
                                Scope.COMPILE, a.getArtifact().getClassifier(),
                                a.getArtifact().getFile().getAbsolutePath()))
                        .findFirst();
                Optional<DefaultArtifactDescriptor> pomAsc = resolveResult.stream()
                        .filter(a -> a.getArtifact().getExtension().equals("pom.asc"))
                        .map(a -> new DefaultArtifactDescriptor(a.getArtifact().getGroupId(),
                                a.getArtifact().getArtifactId(), a.getArtifact().getVersion(),
                                ArtifactDescriptorFactory
                                        .toExtension(a.getArtifact().getExtension()),
                                Scope.COMPILE, a.getArtifact().getClassifier(),
                                a.getArtifact().getFile().getAbsolutePath()))
                        .findFirst();

                if (jar.isPresent() && asc.isPresent() && pom.isPresent() && pomAsc.isPresent()) {
                    result = !vs.stream()
                            .filter(v -> !v.verify(jar.get(), asc.get(), pom.get(), pomAsc.get()))
                            .findFirst().isPresent();
                }
                else {
                    result = false;
                }
            }
            catch (Exception e) {
                result = false;
                throw new DependencyVerificationFailedException(
                        String.format("Verification of %s:%s (%s) failed",
                                node.getArtifact().getGroupId(), node.getArtifact().getArtifactId(),
                                node.getArtifact().getVersion()),
                        node.getArtifact().getGroupId(), node.getArtifact().getArtifactId(),
                        node.getArtifact().getVersion(), e);
            }
            finally {
                boolean r = result;
                vs.forEach(v -> v.finish(r));
            }
        }
        return result;
    }

    private String getLatestVersion(String groupId, String artifactId, String range,
            RepositorySystemSession session, List<RemoteRepository> remotes)
            throws VersionRangeResolutionException {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(
                new DefaultArtifact(String.format("%s:%s:%s", groupId, artifactId, range)));
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
                version = getLatestVersion(artifact.group(), artifact.artifact(), "[,)", session,
                        remotes);
            }
            catch (VersionRangeResolutionException e) {
                throw new DependencyResolverException(
                        String.format("Failed to calculate latest version for %s:%s",
                                artifact.group(), artifact.artifact()),
                        e);
            }
        }
        else if (version.startsWith("(") || version.startsWith("[")) {
            try {
                version = getLatestVersion(artifact.group(), artifact.artifact(), version, session,
                        remotes);
            }
            catch (VersionRangeResolutionException e) {
                throw new DependencyResolverException(
                        String.format("Failed to calculate version for %s:%s and range %s",
                                artifact.group(), artifact.artifact(), version),
                        e);
            }
        }
        return version;
    }

    private RepositorySystemSession newSession(RepositorySystem system, Dependency root,
            boolean transformGarph, String... additionalExclusions)
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

        List<String> combinedExclusions = new ArrayList<>(this.exclusions);
        combinedExclusions.addAll(Arrays.asList(additionalExclusions));

        List<Exclusion> exclusions = combinedExclusions.stream().map(e -> {
            String[] parts = e.split(":");
            return new Exclusion(parts[0], parts[1], "", "jar");
        }).collect(Collectors.toList());

        DependencySelector depFilter = new AndDependencySelector(
                new ScopeDependencySelector("test", "provided"), new OptionalDependencySelector(),
                new ExclusionDependencySelector(exclusions));
        if (!transformGarph) {
            session.setDependencyGraphTransformer(null);
        }

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

    protected Dependency createDependencyRoot(ArtifactDescriptor artifactDescriptor) {
        Artifact artifact = null;
        if (artifactDescriptor.classifier() == null) {
            artifact = new DefaultArtifact(String.format("%s:%s:%s:%s", artifactDescriptor.group(),
                    artifactDescriptor.artifact(),
                    artifactDescriptor.extension().toString().toLowerCase(),
                    artifactDescriptor.version()));
            return new Dependency(artifact, "compile");
        }
        else {
            artifact = new DefaultArtifact(String.format("%s:%s:%s:%s:%s",
                    artifactDescriptor.group(), artifactDescriptor.artifact(),
                    artifactDescriptor.extension().toString().toLowerCase(),
                    artifactDescriptor.classifier(), artifactDescriptor.version()));
            return new Dependency(artifact, "compile", true);
        }
    }
}
