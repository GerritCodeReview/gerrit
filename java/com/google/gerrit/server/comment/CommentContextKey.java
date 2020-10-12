package com.google.gerrit.server.comment;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.util.Collection;

/**
 * An identifier of a comment that should be used to load the comment context using {@link
 * CommentContextCache#get(CommentContextKey)}, or {@link CommentContextCache#getAll(Collection)}.
 *
 * <p>The {@link CommentContextCacheImpl} implementation uses this class as the cache key, while
 * replacing the {@link #path()} field with the hashed path.
 */
@AutoValue
public abstract class CommentContextKey {
  abstract Project.NameKey project();

  abstract Change.Id changeId();

  /** The unique comment ID. */
  abstract String id();

  /** File path at which the comment was written. */
  abstract String path();

  abstract Integer patchset();

  abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_CommentContextKey.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder project(Project.NameKey nameKey);

    public abstract Builder changeId(Change.Id changeId);

    public abstract Builder id(String id);

    public abstract Builder path(String path);

    public abstract Builder patchset(Integer patchset);

    public abstract CommentContextKey build();
  }

  public enum Serializer implements CacheSerializer<CommentContextKey> {
    INSTANCE;

    @Override
    public byte[] serialize(CommentContextKey key) {
      return Protos.toByteArray(
          Cache.CommentContextKeyProto.newBuilder()
              .setProject(key.project().get())
              .setChangeId(key.changeId().toString())
              .setPatchset(key.patchset())
              .setPathHash(key.path())
              .setCommentId(key.id())
              .build());
    }

    @Override
    public CommentContextKey deserialize(byte[] in) {
      Cache.CommentContextKeyProto proto =
          Protos.parseUnchecked(Cache.CommentContextKeyProto.parser(), in);
      return CommentContextKey.builder()
          .project(Project.NameKey.parse(proto.getProject()))
          .changeId(Change.Id.tryParse(proto.getChangeId()).get())
          .patchset(proto.getPatchset())
          .id(proto.getCommentId())
          .path(proto.getPathHash())
          .build();
    }
  }
}
