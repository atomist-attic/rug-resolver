package com.atomist.rug.resolver.loader;

import static scala.collection.JavaConverters.asJavaCollectionConverter;
import static scala.collection.JavaConverters.asScalaBufferConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import com.atomist.param.MappedParameter;
import com.atomist.param.Parameter;
import com.atomist.param.ParameterValue;
import com.atomist.param.ParameterValues;
import com.atomist.param.SimpleParameterValues;
import com.atomist.param.Tag;
import com.atomist.project.archive.RugArchiveReader;
import com.atomist.project.archive.Rugs;
import com.atomist.project.common.InvalidParametersException;
import com.atomist.project.common.MissingParametersException;
import com.atomist.project.edit.Applicability;
import com.atomist.project.edit.ModificationAttempt;
import com.atomist.project.edit.ProjectEditor;
import com.atomist.project.generate.ProjectGenerator;
import com.atomist.project.review.ProjectReviewer;
import com.atomist.project.review.ReviewResult;
import com.atomist.rug.RugRuntimeException;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.ArtifactDescriptorFactory;
import com.atomist.rug.resolver.DefaultArtifactDescriptor;
import com.atomist.rug.resolver.DependencyResolver;
import com.atomist.rug.resolver.DependencyResolverException;
import com.atomist.rug.resolver.project.GitInfo;
import com.atomist.rug.resolver.project.ParameterizedAddressableRug;
import com.atomist.rug.resolver.project.ProvenanceInfo;
import com.atomist.rug.resolver.project.ProvenanceInfoArtifactSourceReader;
import com.atomist.rug.runtime.AddressableRug;
import com.atomist.rug.runtime.CommandHandler;
import com.atomist.rug.runtime.EventHandler;
import com.atomist.rug.runtime.ParameterizedRug;
import com.atomist.rug.runtime.ResponseHandler;
import com.atomist.rug.runtime.Rug;
import com.atomist.rug.runtime.SystemEvent;
import com.atomist.rug.runtime.js.RugContext;
import com.atomist.rug.spi.Handlers;
import com.atomist.rug.spi.Secret;
import com.atomist.source.Artifact;
import com.atomist.source.ArtifactSource;
import com.atomist.source.DirectoryArtifact;
import com.atomist.source.FileArtifact;
import com.atomist.source.file.FileSystemArtifactSource;
import com.atomist.source.file.SimpleFileSystemArtifactSourceIdentifier;
import com.atomist.source.file.ZipFileArtifactSourceReader;
import com.atomist.source.file.ZipFileInput;
import com.atomist.tree.content.project.ResourceSpecifier;
import com.atomist.tree.content.project.SimpleResourceSpecifier;
import com.typesafe.scalalogging.Logger;

import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.runtime.AbstractFunction1;

/**
 * Decorate rugs as we load them with provenance information
 */
public class ProvenanceAddingRugLoader implements RugLoader {

    private static final org.slf4j.Logger LOGGER = LoggerFactory
            .getLogger(ProvenanceAddingRugLoader.class);

    private final DependencyResolver resolver;

    public ProvenanceAddingRugLoader(DependencyResolver resolver) {
        this.resolver = resolver;
    }

    public Rugs load(ArtifactDescriptor artifact) throws RugLoaderException {
        return load(artifact, null);
    }

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

        List<AddressableRug> decorated = new ArrayList<>();

        Rugs operations = null;

        // first collect up all the other operations and decorate them
        for (ArtifactDescriptor ad : dependencies) {
            if (!ad.match(artifact.group(), artifact.artifact(), artifact.version(),
                    ArtifactDescriptor.Extension.ZIP)) {
                ArtifactSource artifactSource = createArtifactSource(ad);
                Seq<Rug> justLoaded = loadArtifact(ad, artifactSource, decorated)
                        .allRugs();
                decorated.addAll(decorate(ad, justLoaded, artifactSource));
            }
        }

        // now load the current ones
        for (ArtifactDescriptor ad : dependencies) {
            if (ad.match(artifact.group(), artifact.artifact(), artifact.version(),
                    ArtifactDescriptor.Extension.ZIP)) {
                // Make sure to load the ArtifactSource if it hasn't been provided
                if (source == null) {
                    source = createArtifactSource(ad);
                }
                operations = loadArtifact(ad, source, decorated);
                break;

            }
        }

        if (operations == null) {
            operations = Rugs.Empty();
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Loaded operations for %s:%s:%s: %s", artifact.group(),
                    artifact.artifact(), artifact.version(), operations.toString()));
        }

        return decorate(artifact, operations, source);
    }

    public Rugs load(String group, String artifact, String version) throws RugLoaderException {
        return load(new DefaultArtifactDescriptor(group, artifact, version,
                ArtifactDescriptor.Extension.ZIP));
    }

    public final Rugs load(String group, String artifact, String version, ArtifactSource source)
            throws RugLoaderException {
        return load(new DefaultArtifactDescriptor(group, artifact, version,
                ArtifactDescriptor.Extension.ZIP), source);
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

    protected List<AddressableRug> decorate(ArtifactDescriptor artifact, List<Rug> operations,
            ArtifactSource source) {

        List<ParameterValue> additionalPvs = Collections.emptyList();

        ResourceSpecifier gav = new SimpleResourceSpecifier(artifact.group(), artifact.artifact(),
                artifact.version());

        List<AddressableRug> rugs = new ArrayList<>();

        for (Rug rug : operations) {
            if (rug instanceof ProjectGenerator) {
                rugs.add(new DecoratedProjectGenerator((ProjectGenerator) rug, gav, additionalPvs,
                        source));
            }
            if (rug instanceof ProjectEditor) {
                rugs.add(new DecoratedProjectEditor((ProjectEditor) rug, gav, additionalPvs,
                        source));
            }
            if (rug instanceof ProjectReviewer) {
                rugs.add(new DecoratedProjectReviewer((ProjectReviewer) rug, gav, additionalPvs,
                        source));
            }
            if (rug instanceof CommandHandler) {
                rugs.add(new DecoratedCommandHandler((CommandHandler) rug, gav, source));
            }
            if (rug instanceof EventHandler) {
                rugs.add(new DecoratedEventHandler((EventHandler) rug, gav, source));
            }
            if (rug instanceof ResponseHandler) {
                rugs.add(new DecoratedResponseHandler((ResponseHandler) rug, gav, source));
            }
        }
        return rugs;

    }

    protected Rugs decorate(ArtifactDescriptor ad, Rugs rugs, ArtifactSource source) {
        List<AddressableRug> decorated = decorate(ad,
                new ArrayList<>(asJavaCollectionConverter(rugs.allRugs()).asJavaCollection()),
                source);
        return Rugs.apply(asScalaBufferConverter(decorated).asScala().toSeq());
    }

    protected List<AddressableRug> decorate(ArtifactDescriptor ad, Seq<Rug> rugs,
            ArtifactSource source) {
        return decorate(ad, new ArrayList<>(asJavaCollectionConverter(rugs).asJavaCollection()),
                source);
    }

    protected DependencyResolver dependencyResolver() {
        return resolver;
    }

    protected Rugs loadArtifact(ArtifactDescriptor artifact, ArtifactSource source,
            List<AddressableRug> otherOperations) throws RugLoaderException {
        try {
            return RugArchiveReader.find(source,
                    asScalaBufferConverter(otherOperations).asScala().toList());
        }
        catch (RugRuntimeException e) {
            LOGGER.error(String.format("Failed to load Rug archive for %s:%s:%s", artifact.group(),
                    artifact.artifact(), artifact.version()), e);
            throw new RugLoaderRuntimeException(String.format(
                    "Failed to load Rug archive for %s:%s:%s:\n  %s", artifact.group(),
                    artifact.artifact(), artifact.version(), e.getMessage()), e);
        }
    }

    protected List<ArtifactDescriptor> postProcessArfifactDescriptors(ArtifactDescriptor artifact,
            List<ArtifactDescriptor> dependencies) {
        return dependencies;
    }

    /**
     * Add some extra parameters to an already parameterized rug
     */
    public static class ParameterizedDecoratingRug<T extends ParameterizedRug> extends
            ProvenanceDeocoratingRug<ParameterizedRug> implements ParameterizedAddressableRug {

        private T delegate;

        protected List<Parameter> additionalParameters;
        protected List<ParameterValue> additionalParameterValues;

        public ParameterizedDecoratingRug(T delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameterValues, ArtifactSource artifactSource) {
            super(delegate, gav, artifactSource);
            this.delegate = delegate;
            this.additionalParameterValues = new ArrayList<>(additionalParameterValues);
            this.additionalParameters = new ArrayList<>();
        }

        @Override
        public ParameterValues addDefaultParameterValues(ParameterValues pvs) {
            return delegate.addDefaultParameterValues(pvs);
        }

        @Override
        public void addToArchiveContext(Seq<Rug> rugs) {
            delegate.addToArchiveContext(rugs);
        }

        @Override
        public Seq<Rug> allRugs() {
            return delegate.allRugs();
        }

        @Override
        public Seq<Rug> archiveContext() {
            return delegate.archiveContext();
        }

        @Override
        public boolean areValid(ParameterValues pvs) {
            return delegate.areValid(pvs);
        }

        @Override
        public Seq<AddressableRug> externalContext() {
            return delegate.externalContext();
        }

        @Override
        public Seq<ParameterValue> findInvalidParameterValues(ParameterValues pvs) {
            return delegate.findInvalidParameterValues(pvs);
        }

        @Override
        public Seq<Parameter> findMissingParameters(ParameterValues pvs) {
            return delegate.findMissingParameters(pvs);
        }

        @Override
        public Option<ParameterizedRug> findParameterizedRug(String simpleOrFq) {
            return delegate.findParameterizedRug(simpleOrFq);
        }

        @Override
        public Option<Rug> findRug(String simpleOrFq) {
            return delegate.findRug(simpleOrFq);
        }

        @Override
        public Logger logger() {
            return delegate.logger();
        }

        @Override
        public Seq<Parameter> parameters() {
            List<Parameter> parameters = new ArrayList<>();
            parameters.addAll(this.additionalParameters);
            parameters.addAll(JavaConverters.asJavaCollectionConverter(delegate.parameters())
                    .asJavaCollection());
            return JavaConverters.asScalaBufferConverter(parameters).asScala();
        }

        @Override
        public void validateParameters(ParameterValues poa) throws InvalidParametersException {
            delegate.validateParameters(poa);
        }

        protected ParameterValues decorateParameterValues(ParameterValues poa) {
            return new ParameterValues() {

                @Override
                public scala.collection.immutable.Map<String, ParameterValue> parameterValueMap() {
                    return poa.parameterValueMap();
                }

                @Override
                public Seq<ParameterValue> parameterValues() {
                    Map<String, ParameterValue> pvs = new HashMap<>();
                    pvs.putAll(
                            JavaConverters.mapAsJavaMapConverter(poa.parameterValueMap()).asJava());
                    additionalParameterValues.forEach(p -> pvs.put(p.getName(), p));
                    return JavaConverters.asScalaBufferConverter(new ArrayList<>(pvs.values()))
                            .asScala();
                }

                @Override
                public Object paramValue(String pv) throws IllegalArgumentException {
                    return poa.paramValue(pv);
                }

                @Override
                public String stringParamValue(String pv) throws IllegalArgumentException {
                    return poa.stringParamValue(pv);
                }

                @Override
                public String toString() {
                    return String.format("Parameters: name=%s, values=[%s]", name(),
                            parameterValues());
                }
            };
        }
    }

    /**
     * A Rug that is addressable via mvn and github coorindates
     *
     * @param <T>
     */
    public static class ProvenanceDeocoratingRug<T extends Rug> implements ProvenanceInfo, Rug {

        private String branch;

        private T delegate;
        private ResourceSpecifier gav;
        private String repo;
        private String sha;

        public ProvenanceDeocoratingRug(T delegate, ResourceSpecifier gav,
                ArtifactSource artifactSource) {
            this.delegate = delegate;
            this.gav = gav;
            init(artifactSource);
        }

        @Override
        public void addToArchiveContext(Seq<Rug> rugs) {
            delegate.addToArchiveContext(rugs);
        }

        @Override
        public Seq<Rug> allRugs() {
            return delegate.allRugs();
        }

        @Override
        public Seq<Rug> archiveContext() {
            return delegate.archiveContext();
        }

        @Override
        public String artifact() {
            return gav.artifactId();
        }

        @Override
        public String branch() {
            return branch;
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public Seq<AddressableRug> externalContext() {
            return delegate.externalContext();
        }

        @Override
        public Option<ParameterizedRug> findParameterizedRug(String simpleOrFq) {
            return delegate.findParameterizedRug(simpleOrFq);
        }

        @Override
        public Option<Rug> findRug(String simpleOrFq) {
            return delegate.findRug(simpleOrFq);
        }

        @Override
        public String group() {
            return gav.groupId();
        }

        @Override
        public Logger logger() {
            return delegate.logger();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String repo() {
            return repo;
        }

        @Override
        public String sha() {
            return sha;
        }

        @Override
        public Seq<Tag> tags() {
            return delegate.tags();
        }

        @Override
        public String version() {
            return gav.version();
        }

        /**
         * Extract git stuff
         *
         * @param artifactSource
         */
        private void init(ArtifactSource artifactSource) {
            Optional<GitInfo> provenanceInfoOptional = ProvenanceInfoArtifactSourceReader
                    .read(artifactSource);
            if (provenanceInfoOptional.isPresent()) {
                GitInfo provenanceInfo = provenanceInfoOptional.get();
                repo = provenanceInfo.repo();
                branch = provenanceInfo.branch();
                sha = provenanceInfo.sha();
            }
        }
    }

    /**
     * Add Provenance to a CommandHandler
     */
    private static class DecoratedCommandHandler extends ParameterizedDecoratingRug<CommandHandler>
            implements CommandHandler {

        private CommandHandler delegate;

        public DecoratedCommandHandler(CommandHandler delegate, ResourceSpecifier gav,
                ArtifactSource artifactSource) {
            super(delegate, gav, Collections.emptyList(), artifactSource);
            this.delegate = delegate;
        }

        @Override
        public Option<Handlers.Plan> handle(RugContext ctx, ParameterValues params) {
            return delegate.handle(ctx, params);
        }

        @Override
        public Seq<String> intent() {
            return delegate.intent();
        }

        @Override
        public Seq<MappedParameter> mappedParameters() {
            return delegate.mappedParameters();
        }

        @Override
        public Seq<Secret> secrets() {
            return delegate.secrets();
        }
    }

    private static class DecoratedEventHandler extends ProvenanceDeocoratingRug<EventHandler>
            implements EventHandler {

        private EventHandler delegate;

        public DecoratedEventHandler(EventHandler delegate, ResourceSpecifier gav,
                ArtifactSource artifactSource) {
            super(delegate, gav, artifactSource);
            this.delegate = delegate;
        }

        @Override
        public Option<Handlers.Plan> handle(RugContext ctx, SystemEvent event) {
            return delegate.handle(ctx, event);
        }

        @Override
        public String rootNodeName() {
            return delegate.rootNodeName();
        }
    }

    /**
     * Add ParameterizedDecoratingRug to a ProjectEditor
     */
    private static class DecoratedProjectEditor extends ParameterizedDecoratingRug<ProjectEditor>
            implements ProjectEditor {

        private ProjectEditor delegate;

        public DecoratedProjectEditor(ProjectEditor delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource source) {
            super(delegate, gav, additionalParameters, source);
            this.delegate = delegate;
        }

        @Override
        public Applicability applicability(ArtifactSource as) {
            return delegate.applicability(as);
        }

        @Override
        public boolean meetsPostcondition(ArtifactSource as) {
            return delegate.meetsPostcondition(as);
        }

        @Override
        public ModificationAttempt modify(ArtifactSource as, ParameterValues poa)
                throws MissingParametersException {
            return delegate.modify(as, decorateParameterValues(poa));
        }

        @Override
        public ParameterValues modify$default$2() {
            return delegate.modify$default$2();// editor has a default param now it seems!
        }

        @Override
        public Applicability potentialApplicability(ArtifactSource as) {
            return delegate.potentialApplicability(as);
        }

        @Override
        public Option<ProjectEditor> reverse() {
            return delegate.reverse();
        }

    }

    private static class DecoratedProjectGenerator
            extends ParameterizedDecoratingRug<ProjectGenerator> implements ProjectGenerator {

        private static final Parameter PROJECT_NAME_PARAMETER;
        private static final String PROJECT_NAME_PARAMETER_NAME = "project_name";

        static {
            PROJECT_NAME_PARAMETER = new Parameter(PROJECT_NAME_PARAMETER_NAME);
            PROJECT_NAME_PARAMETER.setDisplayName("Project Name");
            PROJECT_NAME_PARAMETER.describedAs("Name of your new project");
            PROJECT_NAME_PARAMETER.setValidInputDescription(
                    "A valid GitHub repo name containing only alphanumeric, ., -, and _ characters and 21 characters or less to avoid Slack truncating the name when creating a channel for the repo");
            PROJECT_NAME_PARAMETER.setDisplayName("Project Name");
            PROJECT_NAME_PARAMETER.setMinLength(1);
            PROJECT_NAME_PARAMETER.setMaxLength(21);
            PROJECT_NAME_PARAMETER.setRequired(true);
        }

        private ProjectGenerator delegate;

        private boolean hasOwnProjectNameParameter = false;

        public DecoratedProjectGenerator(ProjectGenerator delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource source) {
            super(delegate, gav, additionalParameters, source);
            this.delegate = delegate;
            init();

        }

        @Override
        public Seq<ParameterValue> findInvalidParameterValues(ParameterValues pvs) {
            Seq<ParameterValue> invalidParameterValue = super.findInvalidParameterValues(pvs);
            if (!hasOwnProjectNameParameter
                    && pvs.parameterValueMap().contains(PROJECT_NAME_PARAMETER_NAME)
                    && !PROJECT_NAME_PARAMETER
                            .isValidValue(pvs.stringParamValue(PROJECT_NAME_PARAMETER_NAME))) {
                List<ParameterValue> parameters = new ArrayList<>(JavaConverters
                        .asJavaCollectionConverter(invalidParameterValue).asJavaCollection());
                parameters.add(pvs.parameterValueMap().get(PROJECT_NAME_PARAMETER_NAME).get());
                invalidParameterValue = JavaConverters.asScalaBufferConverter(parameters).asScala();
            }
            return invalidParameterValue;
        }

        @Override
        public Seq<Parameter> findMissingParameters(ParameterValues pvs) {
            Seq<Parameter> missingParameter = super.findMissingParameters(pvs);
            if (!hasOwnProjectNameParameter
                    && !pvs.parameterValueMap().contains(PROJECT_NAME_PARAMETER_NAME)) {
                List<Parameter> parameters = new ArrayList<>(JavaConverters
                        .asJavaCollectionConverter(missingParameter).asJavaCollection());
                parameters.add(PROJECT_NAME_PARAMETER);
                missingParameter = JavaConverters.asScalaBufferConverter(parameters).asScala();
            }
            return missingParameter;
        }

        @Override
        public ArtifactSource generate(String projectName, ParameterValues poa)
                throws InvalidParametersException {
            if (!this.hasOwnProjectNameParameter) {
                List<ParameterValue> pvs = JavaConverters
                        .seqAsJavaListConverter(poa.parameterValues()).asJava();
                Optional<ParameterValue> projectNamePv = pvs.stream()
                        .filter(pv -> pv.getName().equals(PROJECT_NAME_PARAMETER_NAME)).findFirst();
                if (projectNamePv.isPresent()) {
                    pvs = pvs.stream().filter(p -> !p.getName().equals(PROJECT_NAME_PARAMETER_NAME))
                            .collect(Collectors.toList());
                    poa = new SimpleParameterValues(
                            JavaConverters.asScalaBufferConverter(pvs).asScala());
                }
            }
            ArtifactSource source = delegate.generate(projectName, decorateParameterValues(poa));
            return source.filter(new AbstractFunction1<DirectoryArtifact, Object>() {
                @Override
                public Object apply(DirectoryArtifact dir) {
                    // This is required to remove our maven packaging information
                    if (dir.name().equals("META-INF")) {
                        Optional<Artifact> nonMavenArtifact = asJavaCollectionConverter(
                                dir.artifacts()).asJavaCollection().stream()
                                        .filter(a -> !a.path().startsWith("META-INF/maven"))
                                        .findAny();
                        return nonMavenArtifact.isPresent();
                    }
                    return (!dir.path().equals("META-INF/maven"));
                }
            }, new AbstractFunction1<FileArtifact, Object>() {
                @Override
                public Object apply(FileArtifact arg0) {
                    return true;
                }
            });
        }

        private void init() {
            this.hasOwnProjectNameParameter = JavaConverters
                    .asJavaCollectionConverter(delegate.parameters()).asJavaCollection().stream()
                    .anyMatch(p -> p.getName().equals(PROJECT_NAME_PARAMETER_NAME));
            if (!this.hasOwnProjectNameParameter) {
                this.additionalParameters.add(PROJECT_NAME_PARAMETER);
            }
        }
    }

    /**
     * Decorate a ProjectReviewer with Provenance
     */
    private static class DecoratedProjectReviewer
            extends ParameterizedDecoratingRug<ProjectReviewer> implements ProjectReviewer {

        private ProjectReviewer delegate;

        public DecoratedProjectReviewer(ProjectReviewer delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource source) {
            super(delegate, gav, additionalParameters, source);
            this.delegate = delegate;
        }

        @Override
        public ReviewResult review(ArtifactSource as, ParameterValues pos) {
            return delegate.review(as, decorateParameterValues(pos));
        }
    }

    /**
     * Add Provenance to a ResponseHandler
     */
    private static class DecoratedResponseHandler
            extends ParameterizedDecoratingRug<ResponseHandler> implements ResponseHandler {

        private ResponseHandler delegate;

        public DecoratedResponseHandler(ResponseHandler delegate, ResourceSpecifier gav,
                ArtifactSource artifactSource) {
            super(delegate, gav, Collections.emptyList(), artifactSource);
            this.delegate = delegate;
        }

        @Override
        public Option<Handlers.Plan> handle(Handlers.Response response, ParameterValues params) {
            return delegate.handle(response, params);
        }
    }

}
