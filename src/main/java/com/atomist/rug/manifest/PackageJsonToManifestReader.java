package com.atomist.rug.manifest;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import com.atomist.project.ProvenanceInfo;
import com.atomist.project.ProvenanceInfoArtifactSourceReader;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.fasterxml.jackson.databind.ObjectMapper;

import scala.Option;

class PackageJsonToManifestReader {

    private ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public Manifest read(ArtifactSource source) {
        Option<FileArtifact> packageJsonFile = source.findFile(".atomist/package.json");
        if (packageJsonFile.isDefined()) {

            Map<String, Object> pJson = readPackageJson(packageJsonFile.get().content());

            Manifest manifest = new Manifest();

            String artifact = (String) pJson.get("name");
            // Deal with scoped packages
            if (artifact != null && artifact.startsWith("@")) {
                manifest.setGroup(artifact.substring(1, artifact.indexOf('/')));
                manifest.setArtifact(artifact.substring(artifact.indexOf('/') + 1));
            }
            else {
                manifest.setGroup((String) pJson.get("group"));
                manifest.setArtifact((String) pJson.get("name"));
            }
            manifest.setVersion((String) pJson.get("version"));

            Map<String, String> dependencies = (Map<String, String>) pJson.get("dependencies");
            manifest.setRequires(getGav("@atomist/rug", dependencies, source).version());

            dependencies.entrySet().stream().filter(d -> !"@atomist/rug".equals(d.getKey()))
                    .forEach(d -> {
                        String name = d.getKey();
                        Gav gav = getGav(name, dependencies, source);
                        if (gav != null) {
                            manifest.addExtension(gav);
                        }
                    });

            Optional<ProvenanceInfo> provenanceInfoOptional = new ProvenanceInfoArtifactSourceReader().read(source);
            if (provenanceInfoOptional.isPresent()) {
                ProvenanceInfo provenanceInfo = provenanceInfoOptional.get();
                manifest.setRepo(provenanceInfo.repo().get());
                manifest.setBranch(provenanceInfo.branch().get());
                manifest.setSha(provenanceInfo.sha().get());
            }
            
            return ManifestValidator.validate(manifest);
        }
        throw new ManifestException("package.json could not be found in .atomist");
    }

    @SuppressWarnings("unchecked")
    private Gav getGav(String name, Map<String, String> dependencies, ArtifactSource source) {
        if (dependencies.entrySet().stream().filter(d -> name.equals(d.getKey())).findFirst()
                .isPresent()) {
            Option<FileArtifact> packageJsonOption = source
                    .findFile(".atomist/node_modules/" + name + "/package.json");
            if (packageJsonOption.isDefined()) {
                Map<String, Object> packageJson = readPackageJson(
                        packageJsonOption.get().content());
                Map<String, Object> atomistJson = (Map<String, Object>) packageJson.get("atomist");
                return new Gav((String) atomistJson.get("group"),
                        (String) atomistJson.get("artifact"), (String) packageJson.get("version"));
            }
            else {
                throw new ManifestException(String.format(
                        "Declared dependency to %s not installed locally. Please run npm install on your package.json.",
                        name));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPackageJson(String contents) {
        try {
            return mapper.readValue(contents, Map.class);
        }
        catch (IOException e) {
            throw new ManifestException("Error reading package.json", e);
        }
    }
}
