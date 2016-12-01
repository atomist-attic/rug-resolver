package com.atomist.rug.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
import com.atomist.project.archive.Operations;
import com.atomist.project.common.InvalidParametersException;
import com.atomist.project.common.MissingParametersException;
import com.atomist.project.edit.Applicability;
import com.atomist.project.edit.Impact;
import com.atomist.project.edit.ModificationAttempt;
import com.atomist.project.edit.ProjectEditor;
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
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.runtime.AbstractFunction1;

public class DecoratingOperationsLoader extends DefaultOperationsLoader {

    public DecoratingOperationsLoader(DependencyResolver resolver) {
        super(resolver);
    }

    @Override
    protected Operations postProcess(ArtifactDescriptor artifact, Operations operations,
            ArtifactSource source) {
        List<ParameterValue> additionalPvs = Collections.emptyList();

        ResourceSpecifier gav = new SimpleResourceSpecifier(artifact.group(), artifact.artifact(),
                artifact.version());

        List<ProjectGenerator> generators = JavaConversions
                .asJavaCollection(operations.generators()).stream()
                .filter(g -> !g.name().equals("TypeDoc"))
                .map(g -> new DecoratedProjectGenerator(g, gav, additionalPvs, source))
                .collect(Collectors.toList());
        List<ProjectEditor> editors = JavaConversions.asJavaCollection(operations.editors())
                .stream().map(g -> new DecoratedProjectEditor(g, gav, additionalPvs, source))
                .collect(Collectors.toList());
        List<ProjectReviewer> reviewers = JavaConversions.asJavaCollection(operations.reviewers())
                .stream().map(g -> new DecoratedProjectReviewer(g, gav, additionalPvs, source))
                .collect(Collectors.toList());
        List<Executor> executors = JavaConversions.asJavaCollection(operations.executors()).stream()
                .map(g -> new DecoratedExecuter(g, gav, additionalPvs, source))
                .collect(Collectors.toList());

        return new Operations(JavaConversions.asScalaBuffer(generators).toList(),
                JavaConversions.asScalaBuffer(editors).toList(),
                JavaConversions.asScalaBuffer(reviewers).toList(),
                JavaConversions.asScalaBuffer(executors).toList());
    }

    public static class DelegatingProjectOperation<T extends ProjectOperation>
            implements ProjectOperation, ProvenanceInfo {

        private T delegate;
        private ResourceSpecifier gav;
        private List<ParameterValue> additionalParameters;
        private String repo;
        private String branch;
        private String sha;

        public DelegatingProjectOperation(T delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource artifactSource) {
            this.delegate = delegate;
            this.gav = gav;
            this.additionalParameters = additionalParameters;
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
            return delegate.parameters();
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
                    pvs.putAll(JavaConversions.mapAsJavaMap(poa.parameterValueMap()));
                    additionalParameters.stream().forEach(p -> pvs.put(p.getName(), p));
                    return JavaConversions.asScalaBuffer(new ArrayList<>(pvs.values()));
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

        @Override
        public scala.collection.immutable.Set<Impact> impacts() {
            return getDelegate().impacts();
        }
    }

    private static class DecoratedProjectGenerator
            extends DelegatingProjectOperation<ProjectGenerator> implements ProjectGenerator {

        public DecoratedProjectGenerator(ProjectGenerator delegate, ResourceSpecifier gav,
                List<ParameterValue> additionalParameters, ArtifactSource source) {
            super(delegate, gav, additionalParameters, source);
        }

        @Override
        public ArtifactSource generate(ProjectOperationArguments tcc)
                throws InvalidParametersException {
            ArtifactSource source = getDelegate().generate(decorateProjectOperationArguments(tcc));
            return source.filter(new AbstractFunction1<DirectoryArtifact, Object>() {
                @Override
                public Object apply(DirectoryArtifact dir) {
                    // This is required to remove our maven packaging information
                    if (dir.name().equals("META-INF")) {
                        Optional<Artifact> nonMavenArtifact = JavaConversions
                                .asJavaCollection(dir.artifacts()).stream()
                                .filter(a -> !a.path().startsWith("META-INF/maven")).findAny();
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
}
