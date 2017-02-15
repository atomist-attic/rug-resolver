package com.atomist.rug.resolver.metadata;

import com.atomist.param.Parameter;
import com.atomist.param.Tag;
import com.atomist.project.archive.Rugs;
import com.atomist.rug.resolver.ArtifactDescriptor;
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import scala.collection.JavaConverters;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes all the handler/operation metadata to metadata.json for consumption of catalog
 */
public abstract class MetadataWriter {

    public static enum Format {
        JSON, YAML
    }

    public static FileArtifact create(Rugs operationsAndHandlers,
            ArtifactDescriptor artifact, ArtifactSource source, GitInfo info,
            Format format) {
        try {
            ArchiveMetadata metadata = new ArchiveMetadata(operationsAndHandlers, artifact, info);
            String metadataJson = objectMapper(format).writeValueAsString(metadata);
            return new StringFileArtifact("metadata.json", ".atomist", metadataJson);
        }
        catch (JsonProcessingException e) {
            // TODO throw exception
        }
        return null;
    }

    public static FileArtifact create(Rugs operationsAndHandlers,
            ArtifactDescriptor artifact, ArtifactSource source, GitInfo info) {
        return create(operationsAndHandlers, artifact, source, info, Format.JSON);
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

    private static class ArchiveMetadata {

        @JsonProperty
        private String group;

        @JsonProperty
        private String artifact;

        @JsonProperty
        private String version;

        //git branch/hash etc taken from provenance-info
        @JsonProperty
        private Origin origin;

        @JsonProperty
        private List<ProjectOperation> editors;

        @JsonProperty
        private List<ProjectOperation> generators;

        @JsonProperty
        private List<ProjectOperation> reviewers;

        @JsonProperty
        private List<EventHandler> eventHandlers;

        @JsonProperty
        private List<CommandHandler> commandHandlers;

        @JsonProperty
        private List<ResponseHandler> responseHandlers;

        public ArchiveMetadata(Rugs rugs,
                ArtifactDescriptor artifact, GitInfo info) {

          //  Handlers handlers = operationsAndHandlers.handlers();
            this.editors = JavaConverters.asJavaCollectionConverter(rugs.editors())
                    .asJavaCollection().stream().map(ProjectOperation::new).collect(Collectors.toList());
            this.generators = JavaConverters.asJavaCollectionConverter(rugs.generators())
                    .asJavaCollection().stream().map(ProjectOperation::new).collect(Collectors.toList());
            this.reviewers = JavaConverters.asJavaCollectionConverter(rugs.reviewers())
                    .asJavaCollection().stream().map(ProjectOperation::new).collect(Collectors.toList());
            this.eventHandlers = JavaConverters.asJavaCollectionConverter(rugs.eventHandlers())
                    .asJavaCollection().stream().map(EventHandler::new).collect(Collectors.toList());

            this.commandHandlers = JavaConverters.asJavaCollectionConverter(rugs.commandHandlers())
                    .asJavaCollection().stream().map(CommandHandler::new).collect(Collectors.toList());

            this.responseHandlers = JavaConverters.asJavaCollectionConverter(rugs.responseHandlers())
                    .asJavaCollection().stream().map(ResponseHandler::new).collect(Collectors.toList());
            this.group = artifact.group();
            this.artifact = artifact.artifact();
            this.version = artifact.version();

            if (info != null) {
                this.origin = new Origin(info.repo(), info.branch(), info.sha());
            }
        }
    }

    private static class ProjectOperation {

        @JsonProperty
        private String name;

        @JsonProperty
        private String description;

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

    private static class EventHandler {

        @JsonProperty
        private String name;

        @JsonProperty
        private String description;

        @JsonProperty
        private Collection<Tag> tags;

        @JsonProperty
        private String rootNode;

        public EventHandler(com.atomist.rug.runtime.EventHandler handler) {
            name = handler.name();
            description = handler.description();
            rootNode = handler.rootNodeName();
            tags = JavaConverters.asJavaCollectionConverter(handler.tags()).asJavaCollection();
        }
    }

    private static class CommandHandler {

        @JsonProperty
        private String name;

        @JsonProperty
        private String description;

        @JsonProperty
        private Collection<Tag> tags;

        @JsonProperty
        private Collection<String> intent;

        @JsonProperty
        private Collection<Parameter> parameters;

        public CommandHandler(com.atomist.rug.runtime.CommandHandler handler) {
            name = handler.name();
            description = handler.description();
            intent = JavaConverters.asJavaCollectionConverter(handler.intent()).asJavaCollection();
            tags = JavaConverters.asJavaCollectionConverter(handler.tags()).asJavaCollection();
            parameters = JavaConverters.asJavaCollectionConverter(handler.parameters())
                    .asJavaCollection();
        }
    }

    private static class ResponseHandler {

        @JsonProperty
        private String name;

        @JsonProperty
        private String description;

        @JsonProperty
        private Collection<Tag> tags;

        @JsonProperty
        private Collection<Parameter> parameters;

        public ResponseHandler(com.atomist.rug.runtime.ResponseHandler handler) {
            name = handler.name();
            description = handler.description();
            parameters = JavaConverters.asJavaCollectionConverter(handler.parameters())
                    .asJavaCollection();
            tags = JavaConverters.asJavaCollectionConverter(handler.tags()).asJavaCollection();
        }
    }

    private static class Origin {

        @JsonProperty
        private String repo;

        @JsonProperty
        private String branch;

        @JsonProperty
        private String sha;

        public Origin(String repo, String branch, String sha) {
            this.repo = repo;
            this.branch = branch;
            this.sha = sha;
        }
    }

    public static class TagSerializer extends JsonSerializer<Tag> {

        @Override
        public void serialize(Tag value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException, JsonProcessingException {
            gen.writeStartObject();
            gen.writeStringField("name", value.name());
            gen.writeStringField("decription", value.description());
            gen.writeEndObject();
        }
    }

    @SuppressWarnings("serial")
    public static class MetadataModule extends SimpleModule {

        public MetadataModule() {
            addSerializer(Tag.class, new TagSerializer());
        }
    }
}
