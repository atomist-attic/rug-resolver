package com.atomist.project;

import scala.Option;

public class SimpleProvenanceInfo implements ProvenanceInfo {

    private final String repo;
    private final String branch;
    private final String sha;

    public SimpleProvenanceInfo(String repo, String branch, String sha) {
        this.repo = repo;
        this.branch = branch;
        this.sha = sha;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public Option<String> group() {
        return Option.empty();
    }

    @Override
    public Option<String> artifact() {
        return Option.empty();
    }

    @Override
    public Option<String> version() {
        return Option.empty();
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