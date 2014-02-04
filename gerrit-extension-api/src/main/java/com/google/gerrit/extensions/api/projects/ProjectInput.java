package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.common.InheritableBoolean;
import com.google.gerrit.extensions.common.ProjectSubmitType;

import java.util.List;
import java.util.Map;

public class ProjectInput {
  public String name;
  public String parent;
  public String description;
  public boolean permissionsOnly;
  public boolean createEmptyCommit;
  public ProjectSubmitType submitType;
  public List<String> branches;
  public List<String> owners;
  public InheritableBoolean useContributorAgreements;
  public InheritableBoolean useSignedOffBy;
  public InheritableBoolean useContentMerge;
  public InheritableBoolean requireChangeId;
  public String maxObjectSizeLimit;
  public Map<String, Map<String, String>> pluginConfigValues;
}