package com.atomist.rug.resolver.project;

/**
 * Git stuff
 */
public interface GitInfo {
    String branch();

    String repo();

    String sha();
}
