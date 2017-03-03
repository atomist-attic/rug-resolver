package com.atomist.rug.resolver.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;

import io.takari.filemanager.FileManager;

public class LockingFileProcessor implements FileProcessor, Service {

    private FileManager fileManager;

    public LockingFileProcessor() {

    }

    @Override
    public void copy(File source, File target) throws IOException {
        fileManager.copy(source, target);
    }

    @Override
    public long copy(File source, File target, ProgressListener listener) throws IOException {
        return fileManager.copy(source, target, new ProgressListenerAdapter(listener));
    }

    public void initService(ServiceLocator locator) {
        this.fileManager = locator.getService(FileManager.class);
    }

    @Override
    public boolean mkdirs(File directory) {
        return fileManager.mkdirs(directory);
    }

    @Override
    public void move(File source, File target) throws IOException {
        fileManager.move(source, target);
    }

    @Override
    public void write(File target, InputStream source) throws IOException {
        fileManager.write(target, source);
    }

    @Override
    public void write(File target, String data) throws IOException {
        fileManager.write(target, data);
    }

    private class ProgressListenerAdapter implements FileManager.ProgressListener {

        private ProgressListener listener;

        public ProgressListenerAdapter(ProgressListener listener) {
            this.listener = listener;
        }

        @Override
        public void progressed(ByteBuffer buffer) throws IOException {
            listener.progressed(buffer);
        }
    }
}
