package com.atomist.rug.resolver.project;

public class SimpleGitInfo implements GitInfo {

    private final String repo;
    private final String branch;
    private final String sha;

    public SimpleGitInfo(String repo, String branch, String sha) {
        this.repo = repo;
        this.branch = branch;
        this.sha = sha;
    }

    @Override
    public String repo() {
        return repo;
    }

    @Override
    public String branch() {
        return branch;
    }

    @Override
    public String sha() {
        return sha;
    }

}