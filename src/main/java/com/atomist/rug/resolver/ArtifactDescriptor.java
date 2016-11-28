package com.atomist.rug.resolver;

import java.net.URI;
import java.util.List;

public interface ArtifactDescriptor {

    String group();

    String artifact();

    String version();

    Extension extension();

    Scope scope();

    URI uri();

    boolean match(String group, String artifact, String version, Extension extension);

    List<ArtifactDescriptor> dependencies();

    public enum Extension {
        JAR, ZIP
    }

    public enum Scope {
        RUNTIME, COMPILE, TEST, PROVIDED, SYSTEM
    }
}