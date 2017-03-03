package com.atomist.rug.resolver.git;

public class RepositoryDetails {

    private String branch;
    private String repo;
    private String sha;
    private String time;

    public RepositoryDetails(String repo, String branch, String sha, String time) {
        this.repo = repo;
        this.branch = branch;
        this.sha = sha;
        this.time = time;
    }

    public String branch() {
        return branch;
    }

    public String repo() {
        return repo;
    }

    public String sha() {
        return sha;
    }

    public String time() {
        return time;
    }

}
