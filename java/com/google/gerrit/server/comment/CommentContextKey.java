package com.google.gerrit.server.comment;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import java.util.Collection;

/**
 * A light identifier of a comment that should be used to load the comment context using {@link
 * CommentContextCache#get(CommentContextKey)}, or {@link CommentContextCache#getAll(Collection)}.
 */
@AutoValue
public abstract class CommentContextKey {
  public static CommentContextKey create(
      Project.NameKey project,
      Change.Id changeId,
      String commentId,
      String path,
      Integer patchset) {
    return new AutoValue_CommentContextKey(project, changeId, commentId, path, patchset);
  }

  abstract Project.NameKey project();

  abstract Change.Id changeId();

  /** The unique comment ID. */
  abstract String id();

  /** File path at which the comment was written. */
  abstract String path();

  abstract Integer patchset();

  CommentContextCacheImpl.Key asCacheKey() {
    return CommentContextCacheImpl.Key.create(
        project(), changeId(), patchset(), id(), CommentContextCacheImpl.Loader.hashPath(path()));
  }
}
