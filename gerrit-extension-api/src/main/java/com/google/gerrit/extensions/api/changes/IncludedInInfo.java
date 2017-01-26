package com.google.gerrit.extensions.api.changes;

import java.util.Collection;
import java.util.Map;

public class IncludedInInfo {
  public Collection<String> branches;
  public Collection<String> tags;
  public Map<String, Collection<String>> external;

  public IncludedInInfo(Collection<String> branches,
      Collection<String> tags,
      Map<String, Collection<String>> external) {
    this.branches = branches;
    this.tags = tags;
    this.external = external;
  }
}