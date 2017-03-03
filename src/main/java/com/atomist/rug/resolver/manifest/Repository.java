package com.atomist.rug.resolver.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_EMPTY)
public class Repository {

    private String id;
    private String url;

    public Repository(String id, String url) {
        this.id = id;
        this.url = url;
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @JsonProperty("url")
    public String url() {
        return url;
    }

}
