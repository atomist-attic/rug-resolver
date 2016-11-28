package com.atomist.rug.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

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

    @JsonProperty("url")
    public String url() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
