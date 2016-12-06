package com.atomist.rug.git;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class RepositoryDetailsProvider {

    public RepositoryDetails readDetails(File projectRoot) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.readEnvironment().findGitDir().build()) {
            if (repository.getDirectory() == null) {
                return null;
            }

            ObjectId lastCommit = repository.resolve(repository.getFullBranch());
            String sha = lastCommit.abbreviate(7).name();
            String url = repository.getConfig().getString("remote", "origin", "url");
            if (url != null) {
                url = url.replace("git@github.com:", "");
                url = url.replace("https://github.com/", "");
            }
            String branch = repository.getBranch();
            String date = null;
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(lastCommit);
                date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(commit.getCommitTime() * 1000L));
                walk.dispose();
            }

            try (Git git = new Git(repository)) {
                Status status = git.status().call();
                if (!status.isClean()) {
                    sha = sha + "*";
                }
            }
            catch (NoWorkTreeException e) {
                // We don't care if those come up here
            }
            catch (GitAPIException e) {
                // We don't care if those come up here
            }

            return new RepositoryDetails(url, branch, sha, date);
        }
        catch (IllegalArgumentException e) {
            // If jgit can't find a .git directory it throws an IllegalArgumentException
            return null;
        }
    }
}
