package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;

public class Workspace {
  @AutoValue
  public static abstract class Id {
    public abstract Account.Id accountId();
    public abstract Project.NameKey project();
    public abstract String name();
  }

  public static Id id(Account.Id accountId, Project.NameKey project, String workspaceName) {
    return new AutoValue_Workspace_Id(accountId, project, workspaceName);
  }

  private Workspace(){}
}
