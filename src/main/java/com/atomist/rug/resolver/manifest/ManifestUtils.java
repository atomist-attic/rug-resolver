package com.atomist.rug.resolver.manifest;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import com.atomist.project.edit.ProjectEditor;
import com.atomist.project.generate.ProjectGenerator;
import com.atomist.rug.runtime.CommandHandler;
import com.atomist.rug.runtime.EventHandler;
import com.atomist.rug.runtime.ResponseHandler;
import com.atomist.rug.runtime.Rug;

public abstract class ManifestUtils {

    private static PathMatcher matcher = new AntPathMatcher();

    public static boolean excluded(Rug rug, Manifest manifest) {
        if (manifest == null || manifest.excludes() == null) {
            return false;
        }
        Excludes ex = manifest.excludes();
        List<String> patterns = Collections.emptyList();
        if (rug instanceof ProjectEditor) {
            patterns = ex.editors();
        }
        else if (rug instanceof ProjectGenerator) {
            patterns = ex.generators();
        }
        else if (rug instanceof CommandHandler) {
            patterns = ex.commandHandlers();
        }
        else if (rug instanceof EventHandler) {
            patterns = ex.eventHandlers();
        }
        else if (rug instanceof ResponseHandler) {
            patterns = ex.responseHandlers();
        }
        return matches(rug, patterns);
    }
    
    private static boolean matches(Rug rug, Collection<String> patterns) {
        if (patterns == null || patterns.size() == 0) {
            return false;
        }
        else {
            return patterns.stream().filter(p -> matcher.match(p, rug.name())).findFirst().isPresent();
        }
    }   

}
