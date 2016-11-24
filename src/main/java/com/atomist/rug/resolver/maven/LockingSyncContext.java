package com.atomist.rug.resolver.maven;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.takari.filemanager.FileManager;
import io.takari.filemanager.Lock;

public class LockingSyncContext implements SyncContext {
    private static final char SEPARATOR = '~';

    private Logger logger = LoggerFactory.getLogger(LockingFileProcessor.class);

    private final FileManager fileLockManager;
    private final LocalRepositoryManager localRepoMan;
    private final boolean shared;
    private final Map<String, Lock> locks = new LinkedHashMap<String, Lock>();

    public LockingSyncContext(boolean shared, RepositorySystemSession session,
            FileManager fileLockManager, Logger logger) {
        this.shared = shared;
        this.logger = logger;
        this.fileLockManager = fileLockManager;
        this.localRepoMan = session.getLocalRepositoryManager();
    }

    public void acquire(Collection<? extends Artifact> artifacts,
            Collection<? extends Metadata> metadatas) {
        Collection<String> paths = new TreeSet<String>();
        addArtifactPaths(paths, artifacts);
        addMetadataPaths(paths, metadatas);
        File basedir = getLockBasedir();
        for (String path : paths) {
            File file = new File(basedir, path);
            Lock lock = locks.get(path);
            if (lock == null) {
                if (shared) {
                    lock = fileLockManager.readLock(file);
                }
                else {
                    lock = fileLockManager.writeLock(file);
                }
                locks.put(path, lock);
                try {
                    lock.lock();
                }
                catch (IOException e) {
                    logger.warn("Failed to lock file " + lock.getFile() + ": " + e);
                }
            }
        }
    }

    private File getLockBasedir() {
        return new File(localRepoMan.getRepository().getBasedir(), ".locks");
    }

    private void addArtifactPaths(Collection<String> paths,
            Collection<? extends Artifact> artifacts) {
        if (artifacts != null) {
            for (Artifact artifact : artifacts) {
                String path = getPath(artifact);
                paths.add(path);
            }
        }
    }

    private String getPath(Artifact artifact) {
        // NOTE: Don't use LRM.getPath*() as those paths could be different across processes,
        // e.g. due to staging LRMs.
        StringBuilder path = new StringBuilder(128);
        path.append(artifact.getGroupId()).append(SEPARATOR);
        path.append(artifact.getArtifactId()).append(SEPARATOR);
        path.append(artifact.getBaseVersion());
        return path.toString();
    }

    private void addMetadataPaths(Collection<String> paths,
            Collection<? extends Metadata> metadatas) {
        if (metadatas != null) {
            for (Metadata metadata : metadatas) {
                String path = getPath(metadata);
                paths.add(path);
            }
        }
    }

    private String getPath(Metadata metadata) {
        // NOTE: Don't use LRM.getPath*() as those paths could be different across processes,
        // e.g. due to staging.
        StringBuilder path = new StringBuilder(128);
        if (metadata.getGroupId().length() > 0) {
            path.append(metadata.getGroupId());
            if (metadata.getArtifactId().length() > 0) {
                path.append(SEPARATOR).append(metadata.getArtifactId());
                if (metadata.getVersion().length() > 0) {
                    path.append(SEPARATOR).append(metadata.getVersion());
                }
            }
        }
        return path.toString();
    }

    public void close() {
        for (Lock lock : locks.values()) {
            try {
                lock.unlock();
            }
            catch (IOException e) {
                logger.warn("Failed to unlock file " + lock.getFile() + ": " + e);
            }
        }
        locks.clear();
    }
}
