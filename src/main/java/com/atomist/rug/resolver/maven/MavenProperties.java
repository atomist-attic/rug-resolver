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

    private Map<String, Repo> repos = new HashMap<>();
    private List<Repo> pomRepos = new ArrayList<>();
    private List<String> exclusions = new ArrayList<>();
    private boolean offline = false;
    private boolean cacheMetadata = true;
    
    private String repoLocation = System.getProperty("java.io.tmpdir") + "/.m2/repository/"
            + UUID.randomUUID().toString();
    
    public List<String> getExclusions() {
        return exclusions;
    }
    
    public void setExclusions(List<String> exclusions) {
        this.exclusions = exclusions;
    }

    public Map<String, Repo> getRepos() {
        return repos;
    }

    public List<Repo> getPomRepos() {
        return pomRepos;
    }

    public void setRepos(Map<String, Repo> repos) {
        this.repos = repos;
    }

    public void setPomRepos(List<Repo> pomRepos) {
        this.pomRepos = pomRepos;
    }

    public List<RemoteRepository> repositories() {
        return this.repos.entrySet().stream().map(e -> e.getValue().toRepository(e.getKey()))
                .collect(Collectors.toList());
    }

    public void setRepoLocation(String repoLocation) {
        this.repoLocation = repoLocation;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }
    
    public void setCacheMetadata(boolean cacheMetadata) {
        this.cacheMetadata = cacheMetadata;
    }

    public String getRepoLocation() {
        return repoLocation;
    }

    public boolean isOffline() {
        return offline;
    }
    
    public boolean isCacheMetadata() {
        return cacheMetadata;
    }

    public static class Repo {

        private String url;
        private Auth auth = new Auth();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public void setAuth(Auth auth) {
            this.auth = auth;
        }

        public Auth getAuth() {
            return auth;
        }

        public RemoteRepository toRepository(String id) {
            return new RemoteRepository.Builder(id, "default", url)
                    .setAuthentication(auth.authentication()).build();
        }
    }

    public static class Auth {

        private String username;
        private String password;

        public void setPassword(String password) {
            this.password = password;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public String getUsername() {
            return username;
        }

        public Authentication authentication() {
            return new AuthenticationBuilder().addUsername(username).addPassword(password).build();
        }
    }
}