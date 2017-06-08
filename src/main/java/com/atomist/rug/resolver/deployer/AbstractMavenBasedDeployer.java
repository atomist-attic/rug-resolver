package com.atomist.rug.resolver.deployer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

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
import org.springframework.util.Assert;

import com.atomist.project.archive.Rugs;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.manifest.Manifest;
import com.atomist.rug.resolver.manifest.ManifestFactory;
import com.atomist.rug.resolver.manifest.ManifestPomWriter;
import com.atomist.rug.resolver.manifest.ManifestWriter;
import com.atomist.rug.resolver.maven.MavenConfiguration;
import com.atomist.rug.resolver.metadata.MetadataWriter;
import com.atomist.rug.resolver.project.GitInfo;
import com.atomist.rug.resolver.project.ProvenanceInfoArtifactSourceWriter;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.FileEditor;
import com.atomist.source.SimpleSourceUpdateInfo;
import com.atomist.source.StringFileArtifact;
import com.atomist.source.file.StreamingZipFileOutput;
import com.atomist.source.file.ZipFileArtifactSourceWriter;

public abstract class AbstractMavenBasedDeployer implements Deployer {

    private DeployerEventListener listener = new DefaultDeployerEventListener();
    private String localRepository = null;

    public AbstractMavenBasedDeployer(String localRespository) {
        this.localRepository = localRespository;
    }

    @Override
    public void deploy(Rugs operationsAndHandlers, ArtifactSource source,
            ArtifactDescriptor artifact, File root, String clientId) throws IOException {

        String zipFileName = artifact.artifact() + "-" + artifact.version() + "."
                + artifact.extension().toString().toLowerCase();
        File archive = new File(root, ".atomist/target/" + zipFileName);

        Manifest manifest = ManifestFactory.read(source);
        manifest.setGroup(artifact.group());
        manifest.setArtifact(artifact.artifact());
        manifest.setVersion(artifact.version());
        source = generateMetadata(operationsAndHandlers, artifact, source, manifest, clientId);

        writeArtifactSourceToZip(archive, source);
        File pomFile = writePom(manifest, artifact, root);
        File metadataFile = writeMetadataFile(source, artifact, root);

        RepositorySystem system = new MavenConfiguration().repositorySystem();
        RepositorySystemSession session = createRepositorySession();

        Artifact zip = new DefaultArtifact(manifest.group(), manifest.artifact(), "",
                artifact.extension().toString().toLowerCase(), manifest.version());
        zip = zip.setFile(archive);

        Artifact pom = new SubArtifact(zip, "", "pom");
        pom = pom.setFile(pomFile);
        Artifact metadata = new SubArtifact(zip, "metadata", "json");
        metadata = metadata.setFile(metadataFile);

        doWithRepositorySession(system, session, source, manifest, zip, pom, metadata);
    }

    @Override
    public void registerEventListener(DeployerEventListener listener) {
        Assert.notNull(listener, "listener should not be null");
        this.listener = listener;
    }

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

    protected void writeArtifactSourceToZip(File zipFile, ArtifactSource source)
            throws FileNotFoundException {
        if (!zipFile.getParentFile().exists()) {
            zipFile.getParentFile().mkdirs();
        }
        ZipFileArtifactSourceWriter.write(source,
                new StreamingZipFileOutput(zipFile.getName(), new FileOutputStream(zipFile)),
                new SimpleSourceUpdateInfo(zipFile.getName()));
    }

    private ArtifactSource writeMetadata(Rugs operationsAndHandlers, ArtifactDescriptor artifact,
            ArtifactSource source, GitInfo info, String clientId) {
        FileArtifact metadataFile = MetadataWriter.create(operationsAndHandlers, artifact, source,
                info, clientId);

        ArtifactSource result = source.plus(metadataFile);
        listener.metadataFileGenerated(metadataFile);
        return result;

    }

    private File writeMetadataFile(ArtifactSource source, ArtifactDescriptor artifact,
            File projectRoot) {
        String metadataContents = source.findFile(".atomist/metadata.json").get().content();

        File metadataFile = new File(projectRoot, ".atomist/target/" + artifact.artifact() + "-"
                + artifact.version() + "-metadata.json");
        if (!metadataFile.getParentFile().exists()) {
            metadataFile.getParentFile().mkdirs();
        }

        try (PrintStream out = new PrintStream(new FileOutputStream(metadataFile))) {
            out.print(metadataContents);
        }
        catch (FileNotFoundException e) {
            // This can't really happen
        }

        return metadataFile;
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

    private ArtifactSource writePomAndManifest(ArtifactDescriptor artifact, ArtifactSource source,
            Manifest manifest) {
        String manifestContents = new ManifestWriter().write(manifest);
        String manifestPomContents = new ManifestPomWriter().write(manifest, artifact);

        FileArtifact pomArtifact = new StringFileArtifact("pom.xml",
                "META-INF/maven/" + artifact.group() + "/" + artifact.artifact(),
                manifestPomContents);
        source = source.plus(pomArtifact);

        listener.metadataFileGenerated(pomArtifact);

        FileArtifact manifestArtifact = new StringFileArtifact("manifest.yml", ".atomist",
                manifestContents);
        ArtifactSource result = source.edit(new FileEditor() {
            @Override
            public boolean canAffect(FileArtifact f) {
                return f.path().equals(manifestArtifact.path());
            }

            @Override
            public FileArtifact edit(FileArtifact f) {
                return manifestArtifact;
            }
        });

        listener.metadataFileGenerated(manifestArtifact);

        return result;
    }

    private ArtifactSource writeProvenanceInfo(GitInfo provenanceInfo, ArtifactSource source) {
        return ProvenanceInfoArtifactSourceWriter.write(provenanceInfo, source);
    }

    protected abstract void doWithRepositorySession(RepositorySystem system,
            RepositorySystemSession session, ArtifactSource source, Manifest manifest, Artifact zip,
            Artifact pom, Artifact metadata);

    protected ArtifactSource generateMetadata(Rugs operationsAndHandlers,
            ArtifactDescriptor artifact, ArtifactSource source, Manifest manifest, String clientId) {
        listener.metadataGenerationStarted();
        source = writePomAndManifest(artifact, source, manifest);
        GitInfo info = getGitInfo();
        source = writeProvenanceInfo(info, source);
        source = writeMetadata(operationsAndHandlers, artifact, source, info, clientId);
        listener.metadataGenerationFinished();
        return source;
    }

    protected abstract GitInfo getGitInfo();
}
