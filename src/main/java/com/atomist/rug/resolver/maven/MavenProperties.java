package com.atomist.rug.resolver.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "maven")
public class MavenProperties {

    private boolean cacheMetadata = true;
    private List<String> exclusions = new ArrayList<>();
    private boolean offline = false;
    private List<Repo> pomRepos = new ArrayList<>();
    private String repoLocation = System.getProperty("java.io.tmpdir") + "/.m2/repository/"
            + UUID.randomUUID().toString();

    private Map<String, Repo> repos = new HashMap<>();

    public List<String> getExclusions() {
        return exclusions;
    }

    public List<Repo> getPomRepos() {
        return pomRepos;
    }

    public String getRepoLocation() {
        return repoLocation;
    }

    public Map<String, Repo> getRepos() {
        return repos;
    }

    public boolean isCacheMetadata() {
        return cacheMetadata;
    }

    public boolean isOffline() {
        return offline;
    }

    public List<RemoteRepository> repositories() {
        return this.repos.entrySet().stream().map(e -> e.getValue().toRepository(e.getKey()))
                .collect(Collectors.toList());
    }

    public void setCacheMetadata(boolean cacheMetadata) {
        this.cacheMetadata = cacheMetadata;
    }

    public void setExclusions(List<String> exclusions) {
        this.exclusions = exclusions;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public void setPomRepos(List<Repo> pomRepos) {
        this.pomRepos = pomRepos;
    }

    public void setRepoLocation(String repoLocation) {
        this.repoLocation = repoLocation;
    }

    public void setRepos(Map<String, Repo> repos) {
        this.repos = repos;
    }

    public static class Auth {

        private String password;
        private String username;

        public Authentication authentication() {
            return new AuthenticationBuilder().addUsername(username).addPassword(password).build();
        }

        public String getPassword() {
            return password;
        }

        public String getUsername() {
            return username;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    public static class Repo {

        private Auth auth = new Auth();
        private String url;

        public Auth getAuth() {
            return auth;
        }

        public String getUrl() {
            return url;
        }

        public void setAuth(Auth auth) {
            this.auth = auth;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public RemoteRepository toRepository(String id) {
            return new RemoteRepository.Builder(id, "default", url)
                    .setAuthentication(auth.authentication()).build();
        }
    }
}