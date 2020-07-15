package com.google.gerrit.server.comment;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class CommentContextKey {
  public static CommentContextKey create(String id, String path, Integer patchset) {
    return new AutoValue_CommentContextKey(id, path, patchset);
  }

  abstract String id();

  abstract String path();

  abstract Integer patchset();
}
