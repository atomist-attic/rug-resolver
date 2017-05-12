package com.atomist.rug.resolver.manifest;

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
            manifest.dependencies().forEach(d -> sb.append(
                    String.format("  - \"%s:%s:%s\"\n", d.group(), d.artifact(), d.version())));
        }

        if (manifest.extensions() != null && manifest.extensions().size() > 0) {
            sb.append("\n");
            sb.append("extensions:\n");
            manifest.extensions().forEach(d -> sb.append(
                    String.format("  - \"%s:%s:%s\"\n", d.group(), d.artifact(), d.version())));
        }

        if (manifest.repositories() != null && manifest.repositories().size() > 0) {
            sb.append("\n");
            sb.append("repositories:\n");
            manifest.repositories().forEach(
                    d -> sb.append(String.format("  - %s\n    url: \"%s\"\n", d.id(), d.url())));
        }
        
        if (manifest.excludes() != null) {
            Excludes ex = manifest.excludes();
            sb.append("\n");
            sb.append("excludes:");
            if (ex.editors().size() > 0) {
                sb.append("\n");
                sb.append("  editors:");
                ex.editors().forEach(e -> sb.append(String.format("\n    - \"%s\"", e)));
            }
            if (ex.generators().size() > 0) {
                sb.append("\n");
                sb.append("  generators:");
                ex.generators().forEach(e -> sb.append(String.format("\n    - \"%s\"", e)));
            }
            if (ex.commandHandlers().size() > 0) {
                sb.append("\n");
                sb.append("  command_handlers:");
                ex.commandHandlers().forEach(e -> sb.append(String.format("\n    - \"%s\"", e)));
            }
            if (ex.eventHandlers().size() > 0) {
                sb.append("\n");
                sb.append("  event_handlers:");
                ex.eventHandlers().forEach(e -> sb.append(String.format("\n    - \"%s\"", e)));
            }
            if (ex.responseHandlers().size() > 0) {
                sb.append("\n");
                sb.append("  response_handlers:");
                ex.responseHandlers().forEach(e -> sb.append(String.format("\n    - \"%s\"", e)));
            }
        }

        return sb.toString();
    }
}
