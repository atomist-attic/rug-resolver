package com.atomist.rug.resolver.loader;

import com.atomist.param.Parameter;
import com.atomist.param.ParameterValue;
import com.atomist.param.ParameterValues;
import com.atomist.param.SimpleParameterValues;
import com.atomist.param.Tag;
import com.atomist.project.archive.Rugs;
import com.atomist.project.common.InvalidParametersException;
import com.atomist.project.common.MissingParametersException;
import com.atomist.project.edit.Applicability;
import com.atomist.project.edit.ModificationAttempt;
import com.atomist.project.edit.ProjectEditor;
import com.atomist.project.generate.ProjectGenerator;
import com.atomist.project.review.ProjectReviewer;
import com.atomist.project.review.ReviewResult;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.DependencyResolver;
import com.atomist.rug.resolver.project.GitInfo;
import com.atomist.rug.resolver.project.ParameterizedAddressableRug;
import com.atomist.rug.resolver.project.ProvenanceInfo;
import com.atomist.rug.resolver.project.ProvenanceInfoArtifactSourceReader;
import com.atomist.rug.runtime.CommandHandler;
import com.atomist.rug.runtime.EventHandler;
import com.atomist.rug.runtime.InstructionResponse;
import com.atomist.rug.runtime.ParameterizedRug;
import com.atomist.rug.runtime.ResponseHandler;
import com.atomist.rug.runtime.Rug;
import com.atomist.rug.runtime.SystemEvent;
import com.atomist.rug.runtime.js.RugContext;
import com.atomist.rug.spi.Handlers;
import com.atomist.source.Artifact;
import com.atomist.source.ArtifactSource;
import com.atomist.source.DirectoryArtifact;
import com.atomist.source.FileArtifact;
import com.atomist.tree.content.project.ResourceSpecifier;
import com.atomist.tree.content.project.SimpleResourceSpecifier;
import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.runtime.AbstractFunction1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static scala.collection.JavaConverters.asJavaCollectionConverter;
import static scala.collection.JavaConverters.asScalaBufferConverter;

public class DecoratingRugLoader extends BaseRugLoader {

    public DecoratingRugLoader(DependencyResolver resolver) {
        super(resolver);
    }

    @Override
    protected Rugs postProcess(ArtifactDescriptor artifact, Rugs operations,
            ArtifactSource source) {

        List<ParameterValue> additionalPvs = Collections.emptyList();

        ResourceSpecifier gav = new SimpleResourceSpecifier(artifact.group(), artifact.artifact(),
                artifact.version());

        List<ProjectGenerator> generators = asJavaCollectionConverter(operations.generators())
                .asJavaCollection().stream().filter(g -> !g.name().equals("TypeDoc"))
                .map(g -> new DecoratedProjectGenerator(g, gav, additionalPvs, source))
                .sorted(comparing(Rug::name)).collect(Collectors.toList());
        List<ProjectEditor> editors = asJavaCollectionConverter(operations.editors())
                .asJavaCollection().stream()
                .map(g -> new DecoratedProjectEditor(g, gav, additionalPvs, source))
                .sorted(comparing(Rug::name)).collect(Collectors.toList());
        List<ProjectReviewer> reviewers = asJavaCollectionConverter(operations.reviewers())
                .asJavaCollection().stream()
                .map(g -> new DecoratedProjectReviewer(g, gav, additionalPvs, source))
                .sorted(Comparator.comparing(Rug::name)).collect(Collectors.toList());

        List<CommandHandler> commandHandlers = asJavaCollectionConverter(
                operations.commandHandlers()).asJavaCollection().stream()
                        .map(g -> new DecoratedCommandHandler(g, gav, source))
                        .sorted(Comparator.comparing(Rug::name)).collect(Collectors.toList());

        List<EventHandler> eventHandlers = asJavaCollectionConverter(operations.eventHandlers())
                .asJavaCollection().stream().map(g -> new DecoratedEventHandler(g, gav, source))
                .sorted(Comparator.comparing(Rug::name)).collect(Collectors.toList());

        List<ResponseHandler> responseHandlers = asJavaCollectionConverter(
                operations.responseHandlers()).asJavaCollection().stream()
                        .map(g -> new DecoratedResponseHandler(g, gav, source))
                        .sorted(Comparator.comparing(Rug::name)).collect(Collectors.toList());

        return new Rugs(asScalaBufferConverter(editors).asScala().toSeq(),
                asScalaBufferConverter(generators).asScala().toSeq(),
                asScalaBufferConverter(reviewers).asScala().toSeq(),
                asScalaBufferConverter(commandHandlers).asScala().toSeq(),
                asScalaBufferConverter(eventHandlers).asScala().toSeq(),
                asScalaBufferConverter(responseHandlers).asScala().toSeq());
    }

    /**
     * A Rug that is addressable via mvn and github coorindates
     * @param <T>
     */
    public static class ProvenanceDeocoratingRug<T extends Rug> implements ProvenanceInfo {

        private T delegate;

        private ResourceSpecifier gav;
        private String repo;
        private String branch;
        private String sha;

        public ProvenanceDeocoratingRug(T delegate, ResourceSpecifier gav,
                ArtifactSource artifactSource) {
            this.delegate = delegate;
            this.gav = gav;
            init(artifactSource);
        }

        /**
         * Extract git stuff
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

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public Seq<Tag> tags() {
            return delegate.tags();
        }

        @Override
        public String artifact() {
            return gav.artifactId();
        }

        @Override
        public String group() {
            return gav.groupId();
        }

        @Override
        public String version() {
            return gav.version();
        }

        @Override
        public String repo() {
            return repo;
        }

        @Override
        public String branch() {
            return branch;
        }

        @Override
        public String sha() {
            return sha;
        }
    }

    /**
     * Add some extra parameters to an already parameterized rug
     * 
     */
    public static class ParameterizedDecoratingRug<T extends ParameterizedRug> extends
            ProvenanceDeocoratingRug<ParameterizedRug> implements ParameterizedAddressableRug {

        private T delegate;

        protected List<ParameterValue> additionalParameterValues;
        protected List<Parameter> additionalParameters;

        public ParameterizedDecoratingRug(T delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameterValues, ArtifactSource artifactSource) {
            super(delegate, gav, artifactSource);
            this.delegate = delegate;
            this.additionalParameterValues = new ArrayList<>(additionalParameterValues);
            this.additionalParameters = new ArrayList<>();
        }

        @Override
        public Seq<Parameter> parameters() {
            List<Parameter> parameters = new ArrayList<>();
            parameters.addAll(this.additionalParameters);
            parameters.addAll(JavaConverters.asJavaCollectionConverter(delegate.parameters())
                    .asJavaCollection());
            return JavaConverters.asScalaBufferConverter(parameters).asScala();
        }

        protected ParameterValues decorateParameterValues(ParameterValues poa) {
            return new ParameterValues() {

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
                public String toString() {
                    return String.format("Parameters: name=%s, values=[%s]", name(),
                            parameterValues());
                }

                @Override
                public Object paramValue(String pv) throws IllegalArgumentException {
                    return poa.paramValue(pv);
                }

                @Override
                public scala.collection.immutable.Map<String, ParameterValue> parameterValueMap() {
                    return poa.parameterValueMap();
                }

                @Override
                public String stringParamValue(String pv) throws IllegalArgumentException {
                    return poa.stringParamValue(pv);
                }
            };
        }

        @Override
        public boolean areValid(ParameterValues pvs) {
            return delegate.areValid(pvs);
        }

        @Override
        public Seq<ParameterValue> findInvalidParameterValues(ParameterValues pvs) {
            return delegate.findInvalidParameterValues(pvs);
        }

        @Override
        public Seq<Parameter> findMissingParameters(ParameterValues pvs) {
            return delegate.findMissingParameters(pvs);
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
        public ModificationAttempt modify(ArtifactSource as, ParameterValues poa)
                throws MissingParametersException {
            return delegate.modify(as, decorateParameterValues(poa));
        }

        @Override
        public Applicability potentialApplicability(ArtifactSource as) {
            return delegate.potentialApplicability(as);
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
        public Option<ProjectEditor> reverse() {
            return delegate.reverse();
        }

    }

    private static class DecoratedProjectGenerator
            extends ParameterizedDecoratingRug<ProjectGenerator> implements ProjectGenerator {

        private static final String PROJECT_NAME_PARAMETER_NAME = "project_name";
        private static final Parameter PROJECT_NAME_PARAMETER;

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

        private boolean hasOwnProjectNameParameter = false;

        private ProjectGenerator delegate;

        public DecoratedProjectGenerator(ProjectGenerator delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource source) {
            super(delegate, gav, additionalParameters, source);
            this.delegate = delegate;
            init();

        }

        private void init() {
            this.hasOwnProjectNameParameter = JavaConverters
                    .asJavaCollectionConverter(delegate.parameters()).asJavaCollection().stream()
                    .anyMatch(p -> p.getName().equals(PROJECT_NAME_PARAMETER_NAME));
            if (!this.hasOwnProjectNameParameter) {
                this.additionalParameters.add(PROJECT_NAME_PARAMETER);
            }
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
        public Option<Handlers.Plan> handle(InstructionResponse response, ParameterValues params) {
            return delegate.handle(response, params);
        }
    }
}
