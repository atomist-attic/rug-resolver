package com.atomist.rug.resolver.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_EMPTY)
public class Gav {

    public static Gav formString(String coord) {
        if (coord == null) {
            throw new IllegalArgumentException("gav should not be null");
        }
        String[] parts = coord.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("gav should be of form group:artifact:version");
        }
        return new Gav(parts[0], parts[1], parts[2]);
    }
    private String artifact;
    private String group;

    private String version;

    public Gav() {
    }

    public Gav(String group, String artifact, String version) {
        this.group = group;
        this.artifact = artifact;
        if (version != null) {
            this.version = version.replace(" ", "");
        }
    }

    @JsonProperty("artifact")
    public String artifact() {
        return artifact;
    }

    @JsonProperty("group")
    public String group() {
        return group;
    }

    public void setArtifact(String artifact) {
        this.artifact = artifact;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @JsonProperty("version")
    public String version() {
        return version;
    }
}
