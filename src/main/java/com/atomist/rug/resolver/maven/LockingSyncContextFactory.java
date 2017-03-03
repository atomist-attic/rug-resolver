package com.atomist.rug.resolver.maven;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.impl.SyncContextFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.takari.filemanager.FileManager;

/**
 * Created by cdupuis on 06.10.2016.
 */
public class LockingSyncContextFactory implements SyncContextFactory, Service {

    private FileManager fileLockManager;

    private Logger logger = LoggerFactory.getLogger(LockingFileProcessor.class);

    public LockingSyncContextFactory() {
    }

    public void initService(ServiceLocator locator) {
        this.fileLockManager = locator.getService(FileManager.class);
    }

    public SyncContext newInstance(RepositorySystemSession session, boolean shared) {
        return new LockingSyncContext(shared, session, fileLockManager, logger);
    }

}
