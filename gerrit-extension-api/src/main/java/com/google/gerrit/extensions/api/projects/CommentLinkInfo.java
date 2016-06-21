package com.google.gerrit.extensions.api.projects;

public class CommentLinkInfo {
  public String match;
  public String link;
  public String html;
  public Boolean enabled; // null means true

  public transient String name;
}
