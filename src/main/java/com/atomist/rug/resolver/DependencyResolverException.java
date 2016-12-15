package com.atomist.rug.resolver;

import java.util.Collections;
import java.util.List;

import org.eclipse.aether.repository.RemoteRepository;

public class DependencyResolverException extends RuntimeException {

    private static final long serialVersionUID = 8152076269320243265L;

    private List<RemoteRepository> remoteRepositories = Collections.emptyList();

    public DependencyResolverException(String msg) {
        super(msg);
    }

    public DependencyResolverException(String msg, List<RemoteRepository> remoteRepositories) {
        super(msg);
        this.remoteRepositories = remoteRepositories;
    }

    public DependencyResolverException(String msg, Exception e) {
        super(msg, e);
    }

    public DependencyResolverException(String msg, List<RemoteRepository> remoteRepositories,
            Exception e) {
        super(msg, e);
        this.remoteRepositories = remoteRepositories;
    }

    public List<RemoteRepository> getRemoteRepositories() {
        return remoteRepositories;
    }
}
