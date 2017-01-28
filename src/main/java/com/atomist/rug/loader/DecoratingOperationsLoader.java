package com.atomist.rug.loader;

import static scala.collection.JavaConverters.asJavaCollectionConverter;
import static scala.collection.JavaConverters.asScalaBufferConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.atomist.event.SystemEvent;
import com.atomist.event.SystemEventHandler;
import com.atomist.param.Parameter;
import com.atomist.param.ParameterValue;
import com.atomist.param.ParameterValues;
import com.atomist.param.Tag;
import com.atomist.project.Executor;
import com.atomist.project.ProjectOperation;
import com.atomist.project.ProjectOperationArguments;
import com.atomist.project.ProjectOperationInfo;
import com.atomist.project.ProvenanceInfo;
import com.atomist.project.ProvenanceInfoArtifactSourceReader;
import com.atomist.project.SimpleProjectOperationArguments;
import com.atomist.project.archive.Operations;
import com.atomist.project.common.InvalidParametersException;
import com.atomist.project.common.MissingParametersException;
import com.atomist.project.edit.Applicability;
import com.atomist.project.edit.ModificationAttempt;
import com.atomist.project.edit.ProjectEditor;
import com.atomist.project.generate.EditorInvokingProjectGenerator;
import com.atomist.project.generate.ProjectGenerator;
import com.atomist.project.review.ProjectReviewer;
import com.atomist.project.review.ReviewResult;
import com.atomist.rug.kind.service.ServiceSource;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.DependencyResolver;
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

public class DecoratingOperationsLoader extends DefaultHandlerOperationsLoader {

    public DecoratingOperationsLoader(DependencyResolver resolver) {
        super(resolver);
    }

    @Override
    protected List<SystemEventHandler> postProcess(ArtifactDescriptor artifact,
            List<SystemEventHandler> handlers, ArtifactSource source) {
        ResourceSpecifier gav = new SimpleResourceSpecifier(artifact.group(), artifact.artifact(),
                artifact.version());

        return handlers.stream().map(h -> new DecoratedSystemEventHandler(h, gav, source))
                .collect(Collectors.toList());
    }

    @Override
    protected Operations postProcess(ArtifactDescriptor artifact, Operations operations,
            ArtifactSource source) {
        List<ParameterValue> additionalPvs = Collections.emptyList();

        ResourceSpecifier gav = new SimpleResourceSpecifier(artifact.group(), artifact.artifact(),
                artifact.version());

        List<ProjectGenerator> generators = asJavaCollectionConverter(operations.generators())
                .asJavaCollection().stream().filter(g -> !g.name().equals("TypeDoc"))
                .map(g -> new DecoratedProjectGenerator(g, gav, additionalPvs, source))
                .sorted((e1, e2) -> e1.name().compareTo(e2.name())).collect(Collectors.toList());
        List<ProjectEditor> editors = asJavaCollectionConverter(operations.editors())
                .asJavaCollection().stream()
                .map(g -> new DecoratedProjectEditor(g, gav, additionalPvs, source))
                .sorted((e1, e2) -> e1.name().compareTo(e2.name())).collect(Collectors.toList());
        List<ProjectReviewer> reviewers = asJavaCollectionConverter(operations.reviewers())
                .asJavaCollection().stream()
                .map(g -> new DecoratedProjectReviewer(g, gav, additionalPvs, source))
                .sorted((e1, e2) -> e1.name().compareTo(e2.name())).collect(Collectors.toList());
        List<Executor> executors = asJavaCollectionConverter(operations.executors())
                .asJavaCollection().stream()
                .map(g -> new DecoratedExecuter(g, gav, additionalPvs, source))
                .sorted((e1, e2) -> e1.name().compareTo(e2.name())).collect(Collectors.toList());

        return new Operations(asScalaBufferConverter(generators).asScala().toList(),
                asScalaBufferConverter(editors).asScala().toList(),
                asScalaBufferConverter(reviewers).asScala().toList(),
                asScalaBufferConverter(executors).asScala().toList());
    }

    public static class DelegatingProjectOperation<T extends ProjectOperation>
            implements ProjectOperation, ProvenanceInfo {

        private T delegate;
        private ResourceSpecifier gav;
        private String repo;
        private String branch;
        private String sha;
        protected List<ParameterValue> additionalParameterValues;
        protected List<Parameter> additionalParameters;

        public DelegatingProjectOperation(T delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameterValues, ArtifactSource artifactSource) {
            this.delegate = delegate;
            this.gav = gav;
            this.additionalParameterValues = new ArrayList<>(additionalParameterValues);
            this.additionalParameters = new ArrayList<>();
            init(artifactSource);
        }

        private void init(ArtifactSource artifactSource) {
            Optional<ProvenanceInfo> provenanceInfoOptional = new ProvenanceInfoArtifactSourceReader()
                    .read(artifactSource);
            if (provenanceInfoOptional.isPresent()) {
                ProvenanceInfo provenanceInfo = provenanceInfoOptional.get();
                repo = provenanceInfo.repo().get();
                branch = provenanceInfo.branch().get();
                sha = provenanceInfo.sha().get();
            }
        }

        @Override
        public Seq<Tag> tags() {
            return delegate.tags();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public String name() {
            return delegate.name();
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
        public Option<String> group() {
            return Option.apply(gav.groupId());
        }

        @Override
        public Option<String> artifact() {
            return Option.apply(gav.artifactId());
        }

        @Override
        public Option<String> version() {
            return Option.apply(gav.version());
        }

        @Override
        public Option<String> repo() {
            return Option.apply(repo);
        }

        @Override
        public Option<String> branch() {
            return Option.apply(branch);
        }

        @Override
        public Option<String> sha() {
            return Option.apply(sha);
        }

        protected T getDelegate() {
            return delegate;
        }

        protected ProjectOperationArguments decorateProjectOperationArguments(
                ProjectOperationArguments poa) {
            return new ProjectOperationArguments() {

                @Override
                public Seq<ParameterValue> parameterValues() {
                    Map<String, ParameterValue> pvs = new HashMap<>();
                    pvs.putAll(
                            JavaConverters.mapAsJavaMapConverter(poa.parameterValueMap()).asJava());
                    additionalParameterValues.stream().forEach(p -> pvs.put(p.getName(), p));
                    return JavaConverters.asScalaBufferConverter(new ArrayList<>(pvs.values()))
                            .asScala();
                }

                @Override
                public String name() {
                    return poa.name();
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
        public Option<ResourceSpecifier> gav() {
            return getDelegate().gav();
        }

        @Override
        public boolean areValid(ParameterValues pvs) {
            return getDelegate().areValid(pvs);
        }

        @Override
        public Seq<ParameterValue> findInvalidParameterValues(ParameterValues pvs) {
            return getDelegate().findInvalidParameterValues(pvs);
        }

        @Override
        public Seq<Parameter> findMissingParameters(ParameterValues pvs) {
            return getDelegate().findMissingParameters(pvs);
        }

        @Override
        public ProjectOperationInfo describe() {
            return getDelegate().describe();
        }
    }

    private static class DecoratedExecuter extends DelegatingProjectOperation<Executor>
            implements Executor {

        public DecoratedExecuter(Executor delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource source) {
            super(delegate, gav, additionalParameters, source);
        }

        @Override
        public void execute(ServiceSource serviceSource, ProjectOperationArguments poa) {
            getDelegate().execute(serviceSource, decorateProjectOperationArguments(poa));
        }
    }

    private static class DecoratedProjectEditor extends DelegatingProjectOperation<ProjectEditor>
            implements ProjectEditor {

        public DecoratedProjectEditor(ProjectEditor delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource source) {
            super(delegate, gav, additionalParameters, source);
        }

        @Override
        public ModificationAttempt modify(ArtifactSource as, ProjectOperationArguments poa)
                throws MissingParametersException {
            return getDelegate().modify(as, decorateProjectOperationArguments(poa));
        }

        @Override
        public Applicability potentialApplicability(ArtifactSource as) {
            return getDelegate().potentialApplicability(as);
        }

        @Override
        public Applicability applicability(ArtifactSource as) {
            return getDelegate().applicability(as);
        }

        @Override
        public boolean meetsPostcondition(ArtifactSource as) {
            return getDelegate().meetsPostcondition(as);
        }

        @Override
        public Option<ProjectEditor> reverse() {
            return getDelegate().reverse();
        }

    }

    private static class DecoratedProjectGenerator
            extends DelegatingProjectOperation<ProjectGenerator> implements ProjectGenerator {

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

        public DecoratedProjectGenerator(ProjectGenerator delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource source) {
            super(delegate, gav, additionalParameters, source);
            init();
        }

        private void init() {
            this.hasOwnProjectNameParameter = JavaConverters
                    .asJavaCollectionConverter(getDelegate().parameters()).asJavaCollection()
                    .stream().filter(p -> p.getName().equals(PROJECT_NAME_PARAMETER_NAME))
                    .findFirst().isPresent();
            if (!this.hasOwnProjectNameParameter) {
                this.additionalParameters.add(PROJECT_NAME_PARAMETER);
            }
        }

        @Override
        public ArtifactSource generate(String projectName, ProjectOperationArguments poa)
                throws InvalidParametersException {
            if (!this.hasOwnProjectNameParameter) {
                List<ParameterValue> pvs = JavaConverters
                        .seqAsJavaListConverter(poa.parameterValues()).asJava();
                Optional<ParameterValue> projectNamePv = pvs.stream()
                        .filter(pv -> pv.getName().equals(PROJECT_NAME_PARAMETER_NAME)).findFirst();
                if (projectNamePv.isPresent()) {
                    pvs.remove(projectNamePv.get());
                    poa = new SimpleProjectOperationArguments(poa.name(),
                            JavaConverters.asScalaBufferConverter(pvs).asScala());
                }
            }
            ArtifactSource source = getDelegate().generate(projectName,
                    decorateProjectOperationArguments(poa));
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
    
    private static class DecoratedProjectReviewer
            extends DelegatingProjectOperation<ProjectReviewer> implements ProjectReviewer {

        public DecoratedProjectReviewer(ProjectReviewer delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource source) {
            super(delegate, gav, additionalParameters, source);
        }

        @Override
        public ReviewResult review(ArtifactSource as, ProjectOperationArguments pos) {
            return getDelegate().review(as, decorateProjectOperationArguments(pos));
        }
    }

    private static class DecoratedSystemEventHandler implements SystemEventHandler, ProvenanceInfo {

        private String repo;
        private String branch;
        private String sha;
        private ResourceSpecifier gav;

        private SystemEventHandler delegate;

        public DecoratedSystemEventHandler(SystemEventHandler delegate, ResourceSpecifier gav,
                ArtifactSource artifactSource) {
            this.delegate = delegate;
            this.gav = gav;
            init(artifactSource);
        }

        private void init(ArtifactSource artifactSource) {
            Optional<ProvenanceInfo> provenanceInfoOptional = new ProvenanceInfoArtifactSourceReader()
                    .read(artifactSource);
            if (provenanceInfoOptional.isPresent()) {
                ProvenanceInfo provenanceInfo = provenanceInfoOptional.get();
                repo = provenanceInfo.repo().get();
                branch = provenanceInfo.branch().get();
                sha = provenanceInfo.sha().get();
            }
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public void handle(SystemEvent event, ServiceSource serviceSource) {
            delegate.handle(event, serviceSource);
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String rootNodeName() {
            return delegate.rootNodeName();
        }

        @Override
        public Seq<Tag> tags() {
            return delegate.tags();
        }

        @Override
        public Option<String> group() {
            return Option.apply(gav.groupId());
        }

        @Override
        public Option<String> artifact() {
            return Option.apply(gav.artifactId());
        }

        @Override
        public Option<String> version() {
            return Option.apply(gav.version());
        }

        @Override
        public Option<String> repo() {
            return Option.apply(repo);
        }

        @Override
        public Option<String> branch() {
            return Option.apply(branch);
        }

        @Override
        public Option<String> sha() {
            return Option.apply(sha);
        }
    }
}
