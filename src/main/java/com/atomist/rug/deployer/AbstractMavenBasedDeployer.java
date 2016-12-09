package com.atomist.rug.deployer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.repository.ConservativeProxySelector;
import org.eclipse.aether.util.repository.JreProxySelector;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.atomist.param.Parameter;
import com.atomist.param.Tag;
import com.atomist.project.ProjectOperation;
import com.atomist.project.ProvenanceInfo;
import com.atomist.project.ProvenanceInfoArtifactSourceWriter;
import com.atomist.project.archive.Operations;
import com.atomist.rug.loader.DecoratingOperationsLoader;
import com.atomist.rug.loader.DecoratingOperationsLoader.DelegatingProjectOperation;
import com.atomist.rug.manifest.Manifest;
import com.atomist.rug.manifest.ManifestFactory;
import com.atomist.rug.manifest.ManifestPomWriter;
import com.atomist.rug.manifest.ManifestWriter;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.maven.MavenConfiguration;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.FileEditor;
import com.atomist.source.SimpleSourceUpdateInfo;
import com.atomist.source.StringFileArtifact;
import com.atomist.source.file.StreamingZipFileOutput;
import com.atomist.source.file.ZipFileArtifactSourceWriter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;

import scala.collection.JavaConversions;

public abstract class AbstractMavenBasedDeployer implements Deployer {

    private String localRepository = null;

    public AbstractMavenBasedDeployer(String localRespository) {
        this.localRepository = localRespository;
    }

    @Override
    public void deploy(Operations operations, ArtifactSource source, ArtifactDescriptor artifact,
            File root) throws IOException {

        String zipFileName = artifact.artifact() + "-" + artifact.version() + "."
                + artifact.extension().toString().toLowerCase();
        File archive = new File(root, ".atomist/target/" + zipFileName);

        Manifest manifest = ManifestFactory.read(source);
        manifest.setVersion(artifact.version());
        source = writePomAndManifestToArtifactSource(artifact, source, manifest);
        source = writeProvenanceInfoToArtifactSource(getProvenanceInfo(), source);
        source = writeMetadataToArtifactSource(operations, artifact, source);

        writeArtifactSourceToZip(archive, source);
        File pomFile = writePom(manifest, artifact, root);

        RepositorySystem system = new MavenConfiguration().repositorySystem();
        RepositorySystemSession session = createRepositorySession();

        Artifact zip = new DefaultArtifact(manifest.group(), manifest.artifact(), "",
                artifact.extension().toString().toLowerCase(), manifest.version());
        zip = zip.setFile(archive);

        Artifact pom = new SubArtifact(zip, "", "pom");
        pom = pom.setFile(pomFile);

        doWithRepositorySession(system, session, source, manifest, zip, pom);
    }

    protected abstract ProvenanceInfo getProvenanceInfo();

    protected abstract void doWithRepositorySession(RepositorySystem system,
            RepositorySystemSession session, ArtifactSource source, Manifest manifest, Artifact zip,
            Artifact pom);

    private DefaultRepositorySystemSession createRepositorySession() {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setProxySelector(new ConservativeProxySelector(new JreProxySelector()));

        LocalRepository localRepo = new LocalRepository(localRepository);
        try {
            session.setLocalRepositoryManager(
                    new EnhancedLocalRepositoryManagerFactory().newInstance(session, localRepo));
        }
        catch (NoLocalRepositoryManagerException e1) {
        }
        return session;
    }

    private void writeArtifactSourceToZip(File zipFile, ArtifactSource source)
            throws FileNotFoundException {
        if (!zipFile.getParentFile().exists()) {
            zipFile.getParentFile().mkdirs();
        }
        ZipFileArtifactSourceWriter.write(source,
                new StreamingZipFileOutput(zipFile.getName(), new FileOutputStream(zipFile)),
                new SimpleSourceUpdateInfo(zipFile.getName()));
    }

    private ArtifactSource writeProvenanceInfoToArtifactSource(ProvenanceInfo provenanceInfo,
            ArtifactSource source) {
        return new ProvenanceInfoArtifactSourceWriter().write(provenanceInfo, source);
    }

    private File writePom(Manifest manifest, ArtifactDescriptor artifact, File projectRoot) {
        String manifestContents = new ManifestPomWriter().write(manifest, artifact);

        File pomFile = new File(projectRoot,
                ".atomist/target/" + artifact.artifact() + "-" + artifact.version() + ".pom");
        if (!pomFile.getParentFile().exists()) {
            pomFile.getParentFile().mkdirs();
        }

        try (PrintStream out = new PrintStream(new FileOutputStream(pomFile))) {
            out.print(manifestContents);
        }
        catch (FileNotFoundException e) {
            // This can't really happen
        }

        return pomFile;
    }

    private ArtifactSource writePomAndManifestToArtifactSource(ArtifactDescriptor artifact,
            ArtifactSource source, Manifest manifest) {
        String manifestContents = new ManifestWriter().write(manifest);
        String manifestPomContents = new ManifestPomWriter().write(manifest, artifact);

        FileArtifact pomArtifact = new StringFileArtifact("pom.xml",
                "META-INF/maven/" + artifact.group() + "/" + artifact.artifact(),
                manifestPomContents);
        source = source.plus(pomArtifact);

        FileArtifact manifestArtifact = new StringFileArtifact("manifest.yml", ".atomist",
                manifestContents);
        return source.edit(new FileEditor() {
            @Override
            public boolean canAffect(FileArtifact f) {
                return f.path().equals(manifestArtifact.path());
            }

            @Override
            public FileArtifact edit(FileArtifact f) {
                return manifestArtifact;
            }
        });

    }

    private ArtifactSource writeMetadataToArtifactSource(Operations operations,
            ArtifactDescriptor artifact, ArtifactSource source) {
        try {
            ArchiveMetadata metadata = new ArchiveMetadata(operations, artifact);
            String metadataJson = objectMapper().writeValueAsString(metadata);
            FileArtifact metadataFile = new StringFileArtifact("metadata.json", ".atomist",
                    metadataJson);
            return source.plus(metadataFile);
        }
        catch (JsonProcessingException e) {
            // TODO throw exception
        }

        return source;
    }

    private ObjectMapper objectMapper() {
        // ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        ObjectMapper mapper = new ObjectMapper();
        new Jackson2ObjectMapperBuilder()
                .modulesToInstall(new MetadataModule(), new DefaultScalaModule())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .serializationInclusion(JsonInclude.Include.NON_ABSENT)
                .serializationInclusion(JsonInclude.Include.NON_EMPTY)
                .featuresToEnable(SerializationFeature.INDENT_OUTPUT)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .configure(mapper);
        return mapper;
    }

    private static class ArchiveMetadata {

        @JsonProperty
        private String group;
        @JsonProperty
        private String artifact;
        @JsonProperty
        private String version;

        @JsonProperty
        private List<Operation> editors;
        @JsonProperty
        private List<Operation> generators;
        @JsonProperty
        private List<Operation> executors;
        @JsonProperty
        private List<Operation> reviewers;

        public ArchiveMetadata(Operations operations, ArtifactDescriptor artifact) {
            this.editors = JavaConversions.asJavaCollection(operations.editors()).stream()
                    .map(e -> new Operation(e)).collect(Collectors.toList());
            this.generators = JavaConversions.asJavaCollection(operations.generators()).stream()
                    .map(e -> new Operation(e)).collect(Collectors.toList());
            this.executors = JavaConversions.asJavaCollection(operations.executors()).stream()
                    .map(e -> new Operation(e)).collect(Collectors.toList());
            this.reviewers = JavaConversions.asJavaCollection(operations.reviewers()).stream()
                    .map(e -> new Operation(e)).collect(Collectors.toList());
            this.group = artifact.group();
            this.artifact = artifact.artifact();
            this.version = artifact.version();
        }

    }

    private static class Operation {

        @JsonProperty
        private String name;
        @JsonProperty
        private String description;
        @JsonProperty
        private Collection<Parameter> parameters;
        @JsonProperty
        private Collection<Tag> tags;

        public Operation(ProjectOperation operation) {
            this.name = operation.name();
            this.description = operation.description();
            this.parameters = JavaConversions.asJavaCollection(operation.parameters());
            this.tags = JavaConversions.asJavaCollection(operation.tags());
        }
    }

    public class TagSerializer extends JsonSerializer<Tag> {

        @Override
        public void serialize(Tag value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException, JsonProcessingException {
            gen.writeStartObject();
            gen.writeStringField("name", value.name());
            gen.writeStringField("decription", value.description());
            gen.writeEndObject();
        }
    }

    public class MetadataModule extends SimpleModule {

        public MetadataModule() {
            super();
            addSerializer(Tag.class, new TagSerializer());
        }
    }
}
