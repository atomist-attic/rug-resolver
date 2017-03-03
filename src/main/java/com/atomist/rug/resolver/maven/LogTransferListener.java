package com.atomist.rug.resolver.maven;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogTransferListener extends AbstractTransferListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogTransferListener.class);

    @Override
    public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
        LOGGER.warn(messageFrom(event));
    }

    @Override
    public void transferFailed(TransferEvent event) {
        LOGGER.warn(messageFrom(event));
    }

    @Override
    public void transferInitiated(TransferEvent event) throws TransferCancelledException {
        LOGGER.debug(messageFrom(event));
    }

    @Override
    public void transferProgressed(TransferEvent event) throws TransferCancelledException {
        LOGGER.debug(messageFrom(event));
    }

    @Override
    public void transferStarted(TransferEvent event) throws TransferCancelledException {
        LOGGER.debug(messageFrom(event));
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        LOGGER.info(messageFrom(event));
    }

    private String messageFrom(TransferEvent event) {
        StringBuilder message = new StringBuilder();
        message.append(event.getRequestType().toString().toLowerCase()).append(" ");
        message.append(event.getResource().getRepositoryUrl())
                .append(event.getResource().getResourceName()).append(" ");
        message.append(event.getType().toString().toLowerCase());
        return message.toString();
    }
}