package com.google.gerrit.server.comment;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import java.util.Collection;

/**
 * A light identifier of a comment that should be used to load the comment context using {@link
 * CommentContextCache#get(NameKey, Change.Id, CommentContextKey)}, or {@link
 * CommentContextCache#getAll(NameKey, Change.Id, Collection)}.
 */
@AutoValue
public abstract class CommentContextKey {
  public static CommentContextKey create(String id, String path, Integer patchset) {
    return new AutoValue_CommentContextKey(id, path, patchset);
  }

  /** The unique comment ID. */
  abstract String id();

  /** File path at which the comment was written. */
  abstract String path();

  abstract Integer patchset();

  CommentContextCacheImpl.Key asCacheKey(Project.NameKey project, Change.Id changeId) {
    return CommentContextCacheImpl.Key.create(
        project, changeId, patchset(), id(), CommentContextCacheImpl.Loader.hashPath(path()));
  }
}
