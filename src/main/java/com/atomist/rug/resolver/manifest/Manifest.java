package com.atomist.rug.resolver.manifest;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_EMPTY)
public class Manifest extends Gav {

    public static final String ATOMIST_ROOT = ".atomist";

    public static final String FILE_NAME = "manifest.yml";

    private String branch;

    @JsonInclude(Include.NON_EMPTY)
    private List<Gav> dependencies = new ArrayList<>();
    @JsonInclude(Include.NON_EMPTY)
    private List<Gav> extensions = new ArrayList<>();
    private String repo;

    @JsonInclude(Include.NON_EMPTY)
    private List<Repository> repositories = new ArrayList<>();
    private String requires;
    private String sha;

    public Manifest() {
    }

    public Manifest(String group, String artifact, String version, String requires) {
        super(group, artifact, version);
        setRequires(requires);
    }

    public void addDependency(Gav gav) {
        dependencies.add(gav);
    }

    public void addExtension(Gav gav) {
        extensions.add(gav);
    }

    public void addRepository(Repository repository) {
        repositories.add(repository);
    }

    @JsonProperty("branch")
    public String branch() {
        return branch;
    }

    @JsonProperty("dependencies")
    public List<Gav> dependencies() {
        return dependencies;
    }

    @JsonProperty("extensions")
    public List<Gav> extensions() {
        return extensions;
    }

    @JsonProperty("repo")
    public String repo() {
        return repo;
    }

    @JsonProperty("repositories")
    public List<Repository> repositories() {
        return repositories;
    }

    @JsonProperty("requires")
    public String requires() {
        return requires;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public void setRequires(String requires) {
        if (requires != null) {
            this.requires = requires.replace(" ", "");
        }
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    @JsonProperty("sha")
    public String sha() {
        return sha;
    }
}
