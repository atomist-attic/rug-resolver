package com.atomist.rug.resolver.manifest;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_EMPTY)
public class Excludes {

    @JsonProperty
    private List<String> editors = new ArrayList<>();
    @JsonProperty
    private List<String> generators = new ArrayList<>();
    @JsonProperty
    private List<String> reviewers = new ArrayList<>();
    @JsonProperty("command_handlers")
    private List<String> commandHandlers = new ArrayList<>();
    @JsonProperty("event_handlers")
    private List<String> eventHandlers = new ArrayList<>();
    @JsonProperty("responseHandlers")
    private List<String> responseHandlers = new ArrayList<>();

    public List<String> editors() {
        return editors;
    }

    public List<String> generators() {
        return generators;
    }

    public List<String> reviewers() {
        return reviewers;
    }

    public List<String> commandHandlers() {
        return commandHandlers;
    }

    public List<String> eventHandlers() {
        return eventHandlers;
    }

    public List<String> responseHandlers() {
        return responseHandlers;
    }

    public void addEditor(String exclude) {
        this.editors.add(exclude);
    }

    public void addGenerator(String exclude) {
        this.generators.add(exclude);
    }

    public void addReviewer(String exclude) {
        this.reviewers.add(exclude);
    }

    public void addCommandHandler(String exclude) {
        this.commandHandlers.add(exclude);
    }

    public void addEventHandler(String exclude) {
        this.eventHandlers.add(exclude);
    }

    public void addResponseHandler(String exclude) {
        this.responseHandlers.add(exclude);
    }

}
