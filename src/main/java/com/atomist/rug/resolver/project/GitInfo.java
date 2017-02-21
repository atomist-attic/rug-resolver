package com.atomist.rug.resolver.project;

/**
 * Git stuff
 */
public interface GitInfo {
    String repo();

    String branch();

    String sha();
}
