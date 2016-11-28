package com.atomist.project;

import scala.Option;

public interface ProvenanceInfo {

    String name();
    Option<String> group();
    Option<String> artifact();
    Option<String> version();
    Option<String> repo();
    Option<String> branch();
    Option<String> sha();

}
