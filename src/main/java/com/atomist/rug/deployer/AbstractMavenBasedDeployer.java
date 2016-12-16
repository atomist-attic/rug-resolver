package com.atomist.rug.deployer;

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

import com.atomist.project.ProvenanceInfo;
import com.atomist.project.ProvenanceInfoArtifactSourceWriter;
import com.atomist.rug.compiler.typescript.TypeScriptCompiler;
import com.atomist.rug.compiler.typescript.TypeScriptCompilerContext;
import com.atomist.rug.loader.OperationsAndHandlers;
import com.atomist.rug.manifest.Manifest;
import com.atomist.rug.manifest.ManifestFactory;
import com.atomist.rug.manifest.ManifestPomWriter;
import com.atomist.rug.manifest.ManifestWriter;
import com.atomist.rug.metadata.MetadataWriter;
import com.atomist.rug.resolver.ArtifactDescriptor;
import com.atomist.rug.resolver.maven.MavenConfiguration;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.FileEditor;
import com.atomist.source.SimpleSourceUpdateInfo;
import com.atomist.source.StringFileArtifact;
import com.atomist.source.file.StreamingZipFileOutput;
import com.atomist.source.file.ZipFileArtifactSourceWriter;

public abstract class AbstractMavenBasedDeployer implements Deployer {

    private String localRepository = null;
    private DeployerEventListener listener = new DefaultDeployerEventListener();

    public AbstractMavenBasedDeployer(String localRespository) {
        this.localRepository = localRespository;
    }

    @Override
    public void registerEventListener(DeployerEventListener listener) {
        Assert.notNull(listener);
        this.listener = listener;
    }

    @Override
    public void deploy(OperationsAndHandlers operationsAndHandlers, ArtifactSource source,
            ArtifactDescriptor artifact, File root) throws IOException {

        String zipFileName = artifact.artifact() + "-" + artifact.version() + "."
                + artifact.extension().toString().toLowerCase();
        File archive = new File(root, ".atomist/target/" + zipFileName);

        Manifest manifest = ManifestFactory.read(source);
        manifest.setVersion(artifact.version());
        source = generateMetadata(operationsAndHandlers, artifact, source, manifest);
        source = compileTypeScript(artifact, source);

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

    protected ArtifactSource generateMetadata(OperationsAndHandlers operationsAndHandlers,
            ArtifactDescriptor artifact, ArtifactSource source, Manifest manifest) {
        listener.metadataGenerationStarted();
        source = writePomAndManifest(artifact, source, manifest);
        source = writeProvenanceInfo(getProvenanceInfo(), source);
        source = writeMetadata(operationsAndHandlers, artifact, source);
        listener.metadataGenerationFinished();
        return source;
    }

    protected ArtifactSource compileTypeScript(ArtifactDescriptor artifact, ArtifactSource source) {
        listener.compilationStarted();
        TypeScriptCompilerContext compilerContext = new TypeScriptCompilerContext();
        try {
            compilerContext.init();
            TypeScriptCompiler compiler = compilerContext.compiler();
            ArtifactSource result = compiler.compile(source);
            listener.compilationFinished(result.deltaFrom(source));
            return result;
        }
        finally {
            compilerContext.shutdown();
        }
    }

    protected abstract void doWithRepositorySession(RepositorySystem system,
            RepositorySystemSession session, ArtifactSource source, Manifest manifest, Artifact zip,
            Artifact pom, Artifact metadata);

    protected abstract ProvenanceInfo getProvenanceInfo();

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

    private ArtifactSource writeMetadata(OperationsAndHandlers operationsAndHandlers, ArtifactDescriptor artifact,
            ArtifactSource source) {
        FileArtifact metadataFile = new MetadataWriter().create(operationsAndHandlers, artifact, source);

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

    private ArtifactSource writeProvenanceInfo(ProvenanceInfo provenanceInfo,
            ArtifactSource source) {
        return new ProvenanceInfoArtifactSourceWriter().write(provenanceInfo, source);
    }
}
