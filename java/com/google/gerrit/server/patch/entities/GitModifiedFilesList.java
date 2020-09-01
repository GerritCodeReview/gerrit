package com.google.gerrit.server.patch.entities;

import com.google.auto.value.AutoValue;
import java.io.Serializable;
import java.util.List;

@AutoValue
public abstract class GitModifiedFilesList implements Serializable {
  public static GitModifiedFilesList create(List<GitModifiedFile> gitModifiedFiles) {
    return new AutoValue_GitModifiedFilesList(gitModifiedFiles);
  }

  public abstract List<GitModifiedFile> gitModifiedFiles();
}
