package com.atomist.rug.metadata;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.atomist.event.SystemEventHandler;
import com.atomist.param.Parameter;
import com.atomist.param.Tag;
import com.atomist.project.ProjectOperation;
import com.atomist.project.ProvenanceInfo;
import com.atomist.project.archive.Operations;
import com.atomist.rug.loader.Handlers;
import com.atomist.rug.loader.OperationsAndHandlers;
import com.atomist.rug.resolver.ArtifactDescriptor;
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

public class MetadataWriter {

    public static enum Format {
        JSON, YAML
    }

    public static FileArtifact create(OperationsAndHandlers operationsAndHandlers,
            ArtifactDescriptor artifact, ArtifactSource source, ProvenanceInfo info,
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

    public static FileArtifact create(OperationsAndHandlers operationsAndHandlers,
            ArtifactDescriptor artifact, ArtifactSource source, ProvenanceInfo info) {
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

        @JsonProperty
        private Origin origin;

        @JsonProperty
        private List<Operation> editors;

        @JsonProperty
        private List<Operation> generators;

        @JsonProperty
        private List<Operation> executors;

        @JsonProperty
        private List<Operation> reviewers;

        @JsonProperty
        private List<Handler> handlers;

        public ArchiveMetadata(OperationsAndHandlers operationsAndHandlers,
                ArtifactDescriptor artifact, ProvenanceInfo info) {
            Operations operations = operationsAndHandlers.operations();
            Handlers handlers = operationsAndHandlers.handlers();
            this.editors = JavaConverters.asJavaCollectionConverter(operations.editors())
                    .asJavaCollection().stream().map(Operation::new).collect(Collectors.toList());
            this.generators = JavaConverters.asJavaCollectionConverter(operations.generators())
                    .asJavaCollection().stream().map(Operation::new).collect(Collectors.toList());
            this.executors = JavaConverters.asJavaCollectionConverter(operations.executors())
                    .asJavaCollection().stream().map(Operation::new).collect(Collectors.toList());
            this.reviewers = JavaConverters.asJavaCollectionConverter(operations.reviewers())
                    .asJavaCollection().stream().map(Operation::new).collect(Collectors.toList());
            this.handlers = handlers.handlers().stream().map(Handler::new)
                    .collect(Collectors.toList());
            this.group = artifact.group();
            this.artifact = artifact.artifact();
            this.version = artifact.version();

            if (info != null) {
                this.origin = new Origin(info.repo().get(), info.branch().get(), info.sha().get());
            }
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
            // strip out the fully qualified names as we don't want them here
            int ix = operation.name().lastIndexOf('.');
            if (ix > 0) {
                name = operation.name().substring(ix);
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

    private static class Handler {

        @JsonProperty
        private String name;

        @JsonProperty
        private String description;

        @JsonProperty
        private Collection<Tag> tags;

        @JsonProperty(value = "root_node")
        private String rootNode;

        public Handler(SystemEventHandler handler) {
            name = handler.name();
            description = handler.description();
            rootNode = handler.rootNodeName();
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
