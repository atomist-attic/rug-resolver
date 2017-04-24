package com.atomist.rug.resolver.metadata;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.atomist.param.MappedParameter;
import com.atomist.param.Parameter;
import com.atomist.param.Tag;
import com.atomist.project.archive.Rugs;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.manifest.Manifest;
import com.atomist.rug.resolver.manifest.ManifestFactory;
import com.atomist.rug.resolver.manifest.ManifestUtils;
import com.atomist.rug.resolver.project.GitInfo;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.StringFileArtifact;
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

import scala.collection.JavaConverters;

/**
 * Writes all the handler/operation metadata to metadata.json for consumption of catalog
 */
public abstract class MetadataWriter {

    public static FileArtifact create(Rugs operationsAndHandlers, ArtifactDescriptor artifact,
            ArtifactSource source, GitInfo info) {
        return create(operationsAndHandlers, artifact, source, info, Format.JSON);
    }

    public static FileArtifact create(Rugs operationsAndHandlers, ArtifactDescriptor artifact,
            ArtifactSource source, GitInfo info, Format format) {
        return create(operationsAndHandlers, artifact, source, info, format, true);
    }

    public static FileArtifact createWithoutExcludes(Rugs operationsAndHandlers,
            ArtifactDescriptor artifact, ArtifactSource source, GitInfo info) {
        return create(operationsAndHandlers, artifact, source, info, Format.JSON, false);
    }

    private static FileArtifact create(Rugs operationsAndHandlers, ArtifactDescriptor artifact,
            ArtifactSource source, GitInfo info, Format format, boolean exclude) {
        try {
            Manifest manifest = (exclude ? ManifestFactory.read(source) : new Manifest());
            ArchiveMetadata metadata = new ArchiveMetadata(manifest, operationsAndHandlers,
                    artifact, info);
            String metadataJson = objectMapper(format).writeValueAsString(metadata);
            return new StringFileArtifact("metadata.json", ".atomist", metadataJson);
        }
        catch (JsonProcessingException e) {
            // TODO throw exception
        }
        return null;
    }

    private static ObjectMapper objectMapper(Format format) {
        ObjectMapper mapper = null;
        if (format == Format.YAML) {
            mapper = new ObjectMapper(new YAMLFactory());
        }
        else {
            mapper = new ObjectMapper();
        }
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

    public static enum Format {
        JSON, YAML
    }

    @SuppressWarnings("serial")
    public static class MetadataModule extends SimpleModule {

        public MetadataModule() {
            addSerializer(Tag.class, new TagSerializer());
            addSerializer(MappedParameter.class, new MappedParameterSerializer());
        }
    }

    public static class TagSerializer extends JsonSerializer<Tag> {

        @Override
        public void serialize(Tag value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException, JsonProcessingException {
            gen.writeStartObject();
            gen.writeStringField("name", value.name());
            gen.writeStringField("description", value.description());
            gen.writeEndObject();
        }
    }

    public static class MappedParameterSerializer extends JsonSerializer<MappedParameter> {

        @Override
        public void serialize(MappedParameter value, JsonGenerator gen,
                SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeStartObject();
            gen.writeStringField("local_key", value.localKey());
            gen.writeStringField("foreign_key", value.foreignKey());
            gen.writeEndObject();
        }
    }

    private static class ArchiveMetadata {

        @JsonProperty
        private String artifact;

        @JsonProperty("command_handlers")
        private List<CommandHandler> commandHandlers;

        @JsonProperty
        private List<ProjectOperation> editors;

        @JsonProperty("event_handlers")
        private List<EventHandler> eventHandlers;

        @JsonProperty
        private List<ProjectOperation> generators;

        @JsonProperty
        private String group;

        // git branch/hash etc taken from provenance-info
        @JsonProperty
        private Origin origin;

        @JsonProperty("response_handlers")
        private List<ResponseHandler> responseHandlers;

        @JsonProperty
        private String version;

        public ArchiveMetadata(Manifest manifest, Rugs rugs, ArtifactDescriptor artifact,
                GitInfo info) {

            // Handlers handlers = operationsAndHandlers.handlers();
            this.editors = JavaConverters.asJavaCollectionConverter(rugs.editors())
                    .asJavaCollection().stream().filter(p -> !ManifestUtils.excluded(p, manifest))
                    .map(ProjectOperation::new).collect(Collectors.toList());
            this.generators = JavaConverters.asJavaCollectionConverter(rugs.generators())
                    .asJavaCollection().stream().filter(p -> !ManifestUtils.excluded(p, manifest))
                    .map(ProjectOperation::new).collect(Collectors.toList());
            this.eventHandlers = JavaConverters.asJavaCollectionConverter(rugs.eventHandlers())
                    .asJavaCollection().stream().filter(p -> !ManifestUtils.excluded(p, manifest))
                    .map(EventHandler::new).collect(Collectors.toList());
            this.commandHandlers = JavaConverters.asJavaCollectionConverter(rugs.commandHandlers())
                    .asJavaCollection().stream().filter(p -> !ManifestUtils.excluded(p, manifest))
                    .map(CommandHandler::new).collect(Collectors.toList());
            this.responseHandlers = JavaConverters
                    .asJavaCollectionConverter(rugs.responseHandlers()).asJavaCollection().stream()
                    .filter(p -> !ManifestUtils.excluded(p, manifest)).map(ResponseHandler::new)
                    .collect(Collectors.toList());
            this.group = artifact.group();
            this.artifact = artifact.artifact();
            this.version = artifact.version();

            if (info != null) {
                this.origin = new Origin(info.repo(), info.branch(), info.sha());
            }
        }
    }

    private static class CommandHandler {

        @JsonProperty
        private String description;

        @JsonProperty
        private Collection<String> intent;

        @JsonProperty
        private String name;

        @JsonProperty
        private Collection<Parameter> parameters;

        @JsonProperty("mapped_parameters")
        private Collection<MappedParameter> mappedParameters;

        @JsonProperty
        private Collection<String> secrets;

        @JsonProperty
        private Collection<Tag> tags;

        public CommandHandler(com.atomist.rug.runtime.CommandHandler handler) {
            name = handler.name();
            description = handler.description();
            intent = JavaConverters.asJavaCollectionConverter(handler.intent()).asJavaCollection();
            tags = JavaConverters.asJavaCollectionConverter(handler.tags()).asJavaCollection();
            parameters = JavaConverters.asJavaCollectionConverter(handler.parameters())
                    .asJavaCollection();
            mappedParameters = JavaConverters.asJavaCollectionConverter(handler.mappedParameters())
                    .asJavaCollection();
            secrets = JavaConverters.asJavaCollectionConverter(handler.secrets()).asJavaCollection()
                    .stream().map(s -> s.path()).collect(Collectors.toList());
        }
    }

    private static class EventHandler {

        @JsonProperty
        private String description;

        @JsonProperty
        private String name;

        @JsonProperty("root_node")
        private String rootNode;
        
        @JsonProperty
        private Collection<String> secrets;

        @JsonProperty
        private Collection<Tag> tags;

        public EventHandler(com.atomist.rug.runtime.EventHandler handler) {
            name = handler.name();
            description = handler.description();
            rootNode = handler.rootNodeName();
            tags = JavaConverters.asJavaCollectionConverter(handler.tags()).asJavaCollection();
            secrets = JavaConverters.asJavaCollectionConverter(handler.secrets()).asJavaCollection()
                    .stream().map(s -> s.path()).collect(Collectors.toList());
        }
    }

    private static class Origin {

        @JsonProperty
        private String branch;

        @JsonProperty
        private String repo;

        @JsonProperty
        private String sha;

        public Origin(String repo, String branch, String sha) {
            this.repo = repo;
            this.branch = branch;
            this.sha = sha;
        }
    }

    private static class ProjectOperation {

        @JsonProperty
        private String description;

        @JsonProperty
        private String name;

        @JsonProperty
        private Collection<Parameter> parameters;

        @JsonProperty
        private Collection<Tag> tags;

        public ProjectOperation(com.atomist.project.ProjectOperation operation) {
            // strip out the fully qualified names as we don't want them here
            // only comes from DSL based rugs
            int ix = operation.name().lastIndexOf('.');
            if (ix > 0) {
                name = operation.name().substring(ix + 1);
            }
            else {
                name = operation.name();
            }
            description = operation.description();
            parameters = JavaConverters.asJavaCollectionConverter(operation.parameters())
                    .asJavaCollection();
            tags = JavaConverters.asJavaCollectionConverter(operation.tags()).asJavaCollection();
        }
    }

    private static class ResponseHandler {

        @JsonProperty
        private String description;

        @JsonProperty
        private String name;

        @JsonProperty
        private Collection<Parameter> parameters;

        @JsonProperty
        private Collection<Tag> tags;

        public ResponseHandler(com.atomist.rug.runtime.ResponseHandler handler) {
            name = handler.name();
            description = handler.description();
            parameters = JavaConverters.asJavaCollectionConverter(handler.parameters())
                    .asJavaCollection();
            tags = JavaConverters.asJavaCollectionConverter(handler.tags()).asJavaCollection();
        }
    }
}
