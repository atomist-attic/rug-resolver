package com.atomist.rug.git;

public class RepositoryDetails {

    private String repo;
    private String branch;
    private String time;
    private String sha;

    public RepositoryDetails(String repo, String branch, String sha, String time) {
        this.repo = repo;
        this.branch = branch;
        this.sha = sha;
        this.time = time;
    }

    public String repo() {
        return repo;
    }

    public String branch() {
        return branch;
    }

    public String sha() {
        return sha;
    }

    public String time() {
        return time;
    }

}
