package com.atomist.rug.deployer;

import com.atomist.project.ProvenanceInfo;
import com.atomist.rug.manifest.Manifest;
import com.atomist.rug.manifest.ManifestPomWriter;
import com.atomist.rug.manifest.ManifestReader;
import com.atomist.rug.manifest.ManifestWriter;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.maven.MavenConfiguration;
import com.atomist.source.*;
import com.atomist.source.file.StreamingZipFileOutput;
import com.atomist.source.file.ZipFileArtifactSourceWriter;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.repository.ConservativeProxySelector;
import org.eclipse.aether.util.repository.JreProxySelector;

import java.io.*;

public abstract class AbstractAetherBasedDeployer implements Deployer {

    private String localRepository = null;

    public AbstractAetherBasedDeployer(String localRespository) {
        this.localRepository = localRespository;
    }

    @Override
    public void deploy(File root, ArtifactSource source, ArtifactDescriptor artifact)
            throws IOException {

        String zipFileName = artifact.artifact() + "-" + artifact.version() + "."
                + artifact.extension().toString().toLowerCase();
        File archive = new File(root, ".atomist/target/" + zipFileName);

        Manifest manifest = new ManifestReader().read(source);
        manifest.setVersion(artifact.version());
        source = writePomAndManifestToArtifactSource(artifact, source, manifest);
        source = writeProvenanceInfoToArtifactSource(getProvenanceInfo(), source);

        writeArtifactSourceToZip(archive, source);
        File pomFile = writePom(manifest, artifact, root);

        RepositorySystem system = new MavenConfiguration().repositorySystem();
        RepositorySystemSession session = createRepositorySession();

        Artifact zip = new DefaultArtifact(manifest.group(), manifest.artifact(), "",
                artifact.extension().toString().toLowerCase(), manifest.version());
        zip = zip.setFile(archive);

        Artifact pom = new SubArtifact(zip, "", "pom");
        pom = pom.setFile(pomFile);

        doWithRepositorySession(system, session, source, manifest, zip, pom);
    }

    protected abstract ProvenanceInfo getProvenanceInfo();

    protected abstract void doWithRepositorySession(RepositorySystem system,
            RepositorySystemSession session, ArtifactSource source, Manifest manifest, Artifact zip,
            Artifact pom);

    private DefaultRepositorySystemSession createRepositorySession() {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setProxySelector(new ConservativeProxySelector(new JreProxySelector()));

        LocalRepository localRepo = new LocalRepository(localRepository);
        try {
            session.setLocalRepositoryManager(
                    new EnhancedLocalRepositoryManagerFactory().newInstance(session, localRepo));
        }
        catch (NoLocalRepositoryManagerException e1) {
        }
        return session;
    }

    private void writeArtifactSourceToZip(File zipFile, ArtifactSource source)
            throws FileNotFoundException {
        if (!zipFile.getParentFile().exists()) {
            zipFile.getParentFile().mkdirs();
        }
        ZipFileArtifactSourceWriter.write(source,
                new StreamingZipFileOutput(zipFile.getName(), new FileOutputStream(zipFile)),
                new SimpleSourceUpdateInfo(zipFile.getName()));
    }

    private ArtifactSource writeProvenanceInfoToArtifactSource(ProvenanceInfo provenanceInfo,
            ArtifactSource source) {
        if (provenanceInfo == null) {
            return source;
        }

        FileArtifact manifestArtifact = source.findFile(".atomist/manifest.yml").get();
        StringBuilder sb = new StringBuilder(manifestArtifact.content()).append("\n---\n");
        sb.append("repo: \"").append(provenanceInfo.repo().get()).append("\"\n");
        sb.append("branch: \"").append(provenanceInfo.branch().get()).append("\"\n");
        sb.append("sha: \"").append(provenanceInfo.sha().get()).append("\"\n");
        FileArtifact newManifest = new StringFileArtifact("manifest.yml", ".atomist",
                sb.toString());

        return source.edit(new FileEditor() {
            @Override
            public boolean canAffect(FileArtifact f) {
                return f.path().equals(newManifest.path());
            }

            @Override
            public FileArtifact edit(FileArtifact f) {
                return newManifest;
            }
        });
    }

    private File writePom(Manifest manifest, ArtifactDescriptor artifact, File projectRoot) {
        String manifestContents = new ManifestPomWriter().write(manifest, artifact);

        File pomFile = new File(projectRoot,
                ".atomist/target/" + artifact.artifact() + "-" + artifact.version() + ".pom");
        if (!pomFile.getParentFile().exists()) {
            pomFile.getParentFile().mkdirs();
        }

        try (PrintStream out = new PrintStream(new FileOutputStream(pomFile))) {
            out.print(manifestContents);
        }
        catch (FileNotFoundException e) {
            // This can't really happen
        }

        return pomFile;
    }

    private ArtifactSource writePomAndManifestToArtifactSource(ArtifactDescriptor artifact,
            ArtifactSource source, Manifest manifest) {
        String manifestContents = new ManifestWriter().write(manifest);
        String manifestPomContents = new ManifestPomWriter().write(manifest, artifact);

        FileArtifact pomArtifact = new StringFileArtifact("pom.xml",
                "META-INF/maven/" + artifact.group() + "/" + artifact.artifact(),
                manifestPomContents);
        source = source.plus(pomArtifact);

        FileArtifact manifestArtifact = new StringFileArtifact("manifest.yml", ".atomist",
                manifestContents);
        return source.edit(new FileEditor() {
            @Override
            public boolean canAffect(FileArtifact f) {
                return f.path().equals(manifestArtifact.path());
            }

            @Override
            public FileArtifact edit(FileArtifact f) {
                return manifestArtifact;
            }
        });

    }
}
