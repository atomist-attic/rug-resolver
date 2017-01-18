package com.atomist.rug.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atomist.event.SystemEventHandler;
import com.atomist.event.archive.HandlerArchiveReader;
import com.atomist.plan.TreeMaterializer;
import com.atomist.project.ProjectOperation;
import com.atomist.project.archive.DefaultAtomistConfig$;
import com.atomist.project.archive.ProjectOperationArchiveReader;
import com.atomist.rug.BadRugException;
import com.atomist.rug.EmptyRugFunctionRegistry;
import com.atomist.rug.RugRuntimeException;
import com.atomist.rug.kind.DefaultTypeRegistry$;
import com.atomist.rug.kind.dynamic.DefaultViewFinder$;
import com.atomist.rug.kind.service.MessageBuilder;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.ArtifactDescriptorFactory;
import com.atomist.rug.resolver.DefaultArtifactDescriptor;
import com.atomist.rug.resolver.DependencyResolver;
import com.atomist.rug.resolver.DependencyResolverException;
import com.atomist.rug.runtime.rugdsl.DefaultEvaluator;
import com.atomist.source.ArtifactSource;

import scala.Option;
import scala.collection.JavaConversions;

public class DefaultHandlerOperationsLoader extends DefaultOperationsLoader
        implements HandlerOperationsLoader {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(DefaultHandlerOperationsLoader.class);

    public DefaultHandlerOperationsLoader(DependencyResolver resolver) {
        super(resolver);
    }

    @Override
    public Handlers loadHandlers(String teamId, ArtifactDescriptor artifact, MessageBuilder builder,
            TreeMaterializer treeMaterializer) throws OperationsLoaderException {
        return loadHandlers(teamId, artifact, null, builder, treeMaterializer);
    }

    @Override
    public Handlers loadHandlers(String teamId, String group, String artifact, String version,
            MessageBuilder builder, TreeMaterializer treeMaterializer)
            throws OperationsLoaderException {
        return loadHandlers(teamId, group, artifact, version, null, builder, treeMaterializer);
    }

    @Override
    public Handlers loadHandlers(String teamId, String group, String artifact, String version,
            ArtifactSource source, MessageBuilder builder, TreeMaterializer treeMaterializer)
            throws OperationsLoaderException {
        return loadHandlers(teamId, new DefaultArtifactDescriptor(group, artifact, version,
                ArtifactDescriptor.Extension.ZIP), source, builder, treeMaterializer);
    }

    @Override
    public Handlers loadHandlers(String teamId, ArtifactDescriptor artifact, ArtifactSource source,
            MessageBuilder builder, TreeMaterializer treeMaterializer)
            throws OperationsLoaderException {
        String version = null;
        List<ArtifactDescriptor> dependencies = null;

        try {
            version = dependencyResolver().resolveVersion(artifact);
            artifact = ArtifactDescriptorFactory.copyFrom(artifact, version);
            dependencies = dependencyResolver().resolveTransitiveDependencies(artifact);
        }
        catch (DependencyResolverException e) {
            throw new OperationsLoaderException(String.format(
                    "Failed to resolveTransitiveDependencies dependencies for %s:%s:%s",
                    artifact.group(), artifact.artifact(), version), e);
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Loading operations into Rug runtime for %s:%s:%s",
                    artifact.group(), artifact.artifact(), version));
        }

        dependencies = postProcessArfifactDescriptors(artifact, dependencies);

        List<ProjectOperation> otherOperations = new ArrayList<>();
        HandlerArchiveReader handlerReader = handlerReader(treeMaterializer);
        ProjectOperationArchiveReader reader = operationsReader();

        List<SystemEventHandler> handlers = null;
        for (ArtifactDescriptor ad : dependencies) {
            if (ad.match(artifact.group(), artifact.artifact(), artifact.version(),
                    ArtifactDescriptor.Extension.ZIP)) {
                // Make sure to load the ArtifactSource if it hasn't been provided
                if (source == null) {
                    source = createArtifactSource(ad);
                }

                handlers = loadArtifact(teamId, ad, source, handlerReader, otherOperations,
                        builder);
            }
            else {
                ArtifactSource artifactSource = createArtifactSource(ad);
                otherOperations.addAll(JavaConversions.asJavaCollection(
                        loadArtifact(ad, artifactSource, reader, otherOperations).allOperations()));
            }
        }

        if (handlers == null) {
            handlers = Collections.emptyList();
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Loaded %s handlers for %s:%s:%s", handlers.size(),
                    artifact.group(), artifact.artifact(), artifact.version()));
        }

        return new Handlers(postProcess(artifact, handlers, source));
    }

    private HandlerArchiveReader handlerReader(TreeMaterializer treeMaterializer) {
        return new HandlerArchiveReader(treeMaterializer, DefaultAtomistConfig$.MODULE$,
                new DefaultEvaluator(new EmptyRugFunctionRegistry()), DefaultViewFinder$.MODULE$,
                DefaultTypeRegistry$.MODULE$);
    }

    protected List<SystemEventHandler> postProcess(ArtifactDescriptor artifact,
            List<SystemEventHandler> handlers, ArtifactSource source) {
        return handlers;
    }

    protected List<SystemEventHandler> loadArtifact(String teamId, ArtifactDescriptor artifact,
            ArtifactSource source, HandlerArchiveReader reader,
            List<ProjectOperation> otherOperations, MessageBuilder builder)
            throws OperationsLoaderException {
        try {

            return JavaConversions.asJavaCollection(reader.handlers(teamId, source,
                    Option.apply(artifact.group() + "." + artifact.artifact()),
                    JavaConversions.asScalaBuffer(otherOperations).toList(), builder)).stream()
                    .collect(Collectors.toList());
        }
        catch (RugRuntimeException e) {
            LOGGER.error(String.format("Failed to load Rug archive for %s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.version()), e);
            throw new OperationsLoaderRuntimeException(
                    String.format("Failed to load Rug archive for %s:%s:%s:\n  %s", artifact.group(),
                            artifact.artifact(), artifact.version(), e.getMessage()),
                    e);
        }
        catch (BadRugException e) {
            LOGGER.error(String.format("Failed to load Rug archive for %s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.version()), e);
            throw new OperationsLoaderRuntimeException(
                    String.format("Failed to load Rug archive for %s:%s:%s:\n  %s", artifact.group(),
                            artifact.artifact(), artifact.version(), e.getMessage()),
                    e);
        }
    }

}
