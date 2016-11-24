package com.atomist.rug.manifest;

public class ManifestWriter {

    public String write(Manifest manifest) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("group: \"" + manifest.group() + "\"\n");
        sb.append("artifact: \"" + manifest.artifact() + "\"\n");
        sb.append("version: \"" + manifest.version() + "\"\n");
        sb.append("\n");
        sb.append("requires: \"" + manifest.requires() + "\"\n");

        if (manifest.dependencies() != null && manifest.dependencies().size() > 0) {
            sb.append("\n");
            sb.append("dependencies:\n");
            manifest.dependencies().forEach(d -> sb
                    .append(String.format("  - \"%s:%s:%s\"\n", d.group(), d.artifact(), d.version())));
        }

        if (manifest.extensions() != null && manifest.extensions().size() > 0) {
            sb.append("\n");
            sb.append("extensions:\n");
            manifest.extensions().forEach(d -> sb
                    .append(String.format("  - \"%s:%s:%s\"\n", d.group(), d.artifact(), d.version())));
        }

        if (manifest.repositories() != null && manifest.repositories().size() > 0) {
            sb.append("\n");
            sb.append("repositories:");
            manifest.repositories().forEach(
                    d -> sb.append(String.format("  - %s\n    url: \"%s\"", d.id(), d.url())));
        }

        return sb.toString();
    }
}
