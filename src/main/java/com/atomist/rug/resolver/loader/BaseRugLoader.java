package com.atomist.rug.resolver.loader;

import com.atomist.project.archive.DefaultAtomistConfig$;
import com.atomist.project.archive.DefaultRugArchiveReader;
import com.atomist.project.archive.Rugs;
import com.atomist.project.edit.ProjectEditor;
import com.atomist.project.generate.ProjectGenerator;
import com.atomist.project.review.ProjectReviewer;
import com.atomist.rug.EmptyRugDslFunctionRegistry;
import com.atomist.rug.RugRuntimeException;
import com.atomist.rug.kind.DefaultTypeRegistry$;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.ArtifactDescriptorFactory;
import com.atomist.rug.resolver.DefaultArtifactDescriptor;
import com.atomist.rug.resolver.DependencyResolver;
import com.atomist.rug.resolver.DependencyResolverException;
import com.atomist.rug.runtime.CommandHandler;
import com.atomist.rug.runtime.EventHandler;
import com.atomist.rug.runtime.ResponseHandler;
import com.atomist.rug.runtime.Rug;
import com.atomist.rug.runtime.rugdsl.DefaultEvaluator;
import com.atomist.source.ArtifactSource;
import com.atomist.source.file.FileSystemArtifactSource;
import com.atomist.source.file.SimpleFileSystemArtifactSourceIdentifier;
import com.atomist.source.file.ZipFileArtifactSourceReader;
import com.atomist.source.file.ZipFileInput;
import com.atomist.tree.TreeMaterializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import scala.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static scala.collection.JavaConverters.asJavaCollectionConverter;
import static scala.collection.JavaConverters.asScalaBufferConverter;

/**
 * Common stuff for loading operations/handlers
 */
abstract class BaseRugLoader implements RugLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseRugLoader.class);

    private final DependencyResolver resolver;

    private String teamId;
    private TreeMaterializer trees;

    public BaseRugLoader(DependencyResolver resolver, String teamId, TreeMaterializer trees) {
        this.resolver = resolver;
        this.teamId = teamId;
        this.trees = trees;
    }
    
    @Override
    public Rugs load(ArtifactDescriptor artifact) throws RugLoaderException {
        return load(artifact, null);
    }

    @Override
    public Rugs load(String group, String artifact, String version)
            throws RugLoaderException {
        return load(new DefaultArtifactDescriptor(group, artifact, version, ArtifactDescriptor.Extension.ZIP));
    }

    public final Rugs load(String group, String artifact, String version,
            ArtifactSource source) throws RugLoaderException {
        return load(new DefaultArtifactDescriptor(group, artifact, version, ArtifactDescriptor.Extension.ZIP), source);
    }

    @Override
    public final Rugs load(ArtifactDescriptor artifact, ArtifactSource source)
            throws RugLoaderException {

        String version = null;
        List<ArtifactDescriptor> dependencies;

        try {
            version = resolver.resolveVersion(artifact);
            artifact = ArtifactDescriptorFactory.copyFrom(artifact, version);
            dependencies = resolver.resolveTransitiveDependencies(artifact);
        }
        catch (DependencyResolverException e) {
            throw new RugLoaderException(String.format(
                    "Failed to resolveTransitiveDependencies dependencies for %s:%s:%s",
                    artifact.group(), artifact.artifact(), version), e);
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Loading operations into Rug runtime for %s:%s:%s",
                    artifact.group(), artifact.artifact(), version));
        }

        dependencies = postProcessArfifactDescriptors(artifact, dependencies);

        List<Rug> otherOperations = new ArrayList<>();
        DefaultRugArchiveReader reader = operationsReader();

        Rugs operations = null;
        for (ArtifactDescriptor ad : dependencies) {
            if (ad.match(artifact.group(), artifact.artifact(), artifact.version(),
                    ArtifactDescriptor.Extension.ZIP)) {
                // Make sure to load the ArtifactSource if it hasn't been provided
                if (source == null) {
                    source = createArtifactSource(ad);
                }
                
                operations = loadArtifact(ad, source, reader, otherOperations);
            }
            else {
                ArtifactSource artifactSource = createArtifactSource(ad);
                otherOperations.addAll(asJavaCollectionConverter(
                        loadArtifact(ad, artifactSource, reader, otherOperations).projectOperations())
                                .asJavaCollection());
            }
        }

        if (operations == null) {
            operations = new Rugs(
                    asScalaBufferConverter(Collections.<ProjectEditor> emptyList()).asScala(),
                    asScalaBufferConverter(Collections.<ProjectGenerator> emptyList()).asScala(),
                    asScalaBufferConverter(Collections.<ProjectReviewer> emptyList()).asScala(),
                    asScalaBufferConverter(Collections.<CommandHandler> emptyList()).asScala(),
                    asScalaBufferConverter(Collections.<EventHandler> emptyList()).asScala(),
                    asScalaBufferConverter(Collections.<ResponseHandler> emptyList()).asScala());
        }

        if (LOGGER.isInfoEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("editors: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.editorNames()).asJavaCollection(), ", "));
            sb.append("] generators: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.generatorNames()).asJavaCollection(),
                    ", "));
            sb.append("] reviewers: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.reviewerNames()).asJavaCollection(),
                    ", "));
            sb.append("] event handlers: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.eventHandlerNames()).asJavaCollection(),
                    ", "));

            sb.append("] command handlers: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.commandHandlerNames()).asJavaCollection(),
                    ", "));

            sb.append("] response handlers: [");
            sb.append(StringUtils.collectionToDelimitedString(
                    asJavaCollectionConverter(operations.responseHandlerNames()).asJavaCollection(),
                    ", "));

            sb.append("]");
            LOGGER.info(String.format("Loaded operations for %s:%s:%s: %s", artifact.group(),
                    artifact.artifact(), artifact.version(), sb.toString()));
        }

        return postProcess(artifact, operations, source);
    }

    protected DependencyResolver dependencyResolver() {
        return resolver;
    }

    protected List<ArtifactDescriptor> postProcessArfifactDescriptors(ArtifactDescriptor artifact,
            List<ArtifactDescriptor> dependencies) {
        return dependencies;
    }

    protected Rugs postProcess(ArtifactDescriptor artifact, Rugs operations,
            ArtifactSource source) {
        return operations;
    }

    protected ArtifactSource createArtifactSource(ArtifactDescriptor artifact) {
        try {
            File archiveRoot = new File(artifact.uri());
            if (archiveRoot.isFile()) {
                return ZipFileArtifactSourceReader
                        .fromZipSource(new ZipFileInput(new FileInputStream(archiveRoot)));
            }
            else {
                return new FileSystemArtifactSource(
                        new SimpleFileSystemArtifactSourceIdentifier(archiveRoot));
            }
        }
        catch (FileNotFoundException e) {
            LOGGER.warn(String.format("Failed to read Rug archive for %s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.version()), e);
        }
        return null;
    }

    protected Rugs loadArtifact(ArtifactDescriptor artifact, ArtifactSource source,
                                DefaultRugArchiveReader reader, List<Rug> otherOperations)
            throws RugLoaderException {
        try {
            return reader.find(source,
                    Option.apply(artifact.group() + "." + artifact.artifact()),
                    asScalaBufferConverter(otherOperations).asScala().toList());
        }
        catch (RugRuntimeException e) {
            LOGGER.error(String.format("Failed to load Rug archive for %s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.version()), e);
            throw new RugLoaderRuntimeException(
                    String.format("Failed to load Rug archive for %s:%s:%s:\n  %s", artifact.group(),
                            artifact.artifact(), artifact.version(), e.getMessage()),
                    e);
        }
    }

    protected DefaultRugArchiveReader operationsReader() {
        return new DefaultRugArchiveReader(
                teamId,
                trees,
                DefaultAtomistConfig$.MODULE$,
                new DefaultEvaluator(new EmptyRugDslFunctionRegistry()),
                DefaultTypeRegistry$.MODULE$);
    }
}
