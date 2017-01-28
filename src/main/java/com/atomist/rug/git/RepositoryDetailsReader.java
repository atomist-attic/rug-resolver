package com.atomist.rug.git;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public abstract class RepositoryDetailsReader {

    public static Optional<RepositoryDetails> read(File projectRoot) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.readEnvironment().findGitDir().build()) {
            // Verify that we are in a git repository and have a branch
            if (repository.getDirectory() == null && repository.getFullBranch() == null) {
                return Optional.empty();
            }
            
            // Verify that there is a commit in the repository
            ObjectId lastCommit = repository.resolve(repository.getFullBranch());
            if (lastCommit == null) {
                return Optional.empty();
            }
            
            String sha = lastCommit.abbreviate(7).name();
            String url = repository.getConfig().getString("remote", "origin", "url");
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

            if (url != null && branch != null && sha != null) {
                return Optional.of(new RepositoryDetails(url, branch, sha, date));
            }
        }
        catch (IllegalArgumentException e) {
            // If jgit can't find a .git directory it throws an IllegalArgumentException
        }
        return Optional.empty();
    }
}
