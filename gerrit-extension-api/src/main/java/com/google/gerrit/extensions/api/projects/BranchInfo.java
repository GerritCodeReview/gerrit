package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;

import java.util.List;
import java.util.Map;

public class BranchInfo {
  public String ref;
  public String revision;
  public Boolean canDelete;
  public Map<String, ActionInfo> actions;
  public List<WebLinkInfo> webLinks;
}
