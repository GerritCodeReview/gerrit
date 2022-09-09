package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;

/** Stores together numeric {@link Change.Id} and a project name for the change */
@AutoValue
public abstract class ProjectChangeKey {
  public static ProjectChangeKey create(Project.NameKey projectName, Change.Id changeId) {
    return new AutoValue_ProjectChangeKey(projectName, changeId);
  }

  public abstract Project.NameKey projectName();

  public abstract Change.Id changeId();
}
