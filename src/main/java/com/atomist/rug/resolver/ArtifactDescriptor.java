package com.atomist.rug.resolver;

import java.net.URI;
import java.util.List;

public interface ArtifactDescriptor {

    String artifact();

    String classifier();

    List<ArtifactDescriptor> dependencies();

    Extension extension();

    String group();

    boolean match(String group, String artifact, String version, Extension extension);

    Scope scope();

    URI uri();

    String version();

    public enum Extension {
        JAR, JSON, ZIP
    }

    public enum Scope {
        COMPILE, PROVIDED, RUNTIME, SYSTEM, TEST
    }
}