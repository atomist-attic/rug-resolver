package com.atomist.rug.loader;

import com.atomist.plan.TreeMaterializer;
import com.atomist.rug.kind.service.MessageBuilder;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.source.ArtifactSource;

public interface HandlerOperationsLoader extends OperationsLoader {

    Handlers loadHandlers(String teamId, ArtifactDescriptor artifact, ArtifactSource source,
            MessageBuilder builder, TreeMaterializer treeMaterializer)
            throws OperationsLoaderException;

    Handlers loadHandlers(String teamId, String group, String artifact, String version,
            ArtifactSource source, MessageBuilder builder, TreeMaterializer treeMaterializer)
            throws OperationsLoaderException;

    Handlers loadHandlers(String teamId, ArtifactDescriptor artifact, MessageBuilder builder,
            TreeMaterializer treeMaterializer) throws OperationsLoaderException;

    Handlers loadHandlers(String teamId, String group, String artifact, String version,
            MessageBuilder builder, TreeMaterializer treeMaterializer)
            throws OperationsLoaderException;
}
