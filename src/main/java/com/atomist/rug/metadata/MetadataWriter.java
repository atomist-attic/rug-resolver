package com.atomist.rug.metadata;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.atomist.param.Parameter;
import com.atomist.param.Tag;
import com.atomist.project.ProjectOperation;
import com.atomist.project.archive.Operations;
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
import com.fasterxml.jackson.module.scala.DefaultScalaModule;

import scala.collection.JavaConversions;

public class MetadataWriter {

    public FileArtifact create(Operations operations, ArtifactDescriptor artifact,
            ArtifactSource source) {
        try {
            ArchiveMetadata metadata = new ArchiveMetadata(operations, artifact);
            String metadataJson = objectMapper().writeValueAsString(metadata);
            return new StringFileArtifact("metadata.json", ".atomist", metadataJson);
        }
        catch (JsonProcessingException e) {
            // TODO throw exception
        }
        return null;
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

    @SuppressWarnings("serial")
    public class MetadataModule extends SimpleModule {

        public MetadataModule() {
            super();
            addSerializer(Tag.class, new TagSerializer());
        }
    }
}
