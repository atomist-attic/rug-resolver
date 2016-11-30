package com.atomist.rug.git;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class RepositoryDetailsProvider {

    public RepositoryDetails readDetails(File projectRoot) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(projectRoot, ".git"))
                .readEnvironment().findGitDir().build()) {
            if (repository.getBranch() == null) {
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
            return new RepositoryDetails(url, branch, sha, date);
        }
    }
}
