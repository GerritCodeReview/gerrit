// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.comment;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.CacheSerializer;

/**
 * An identifier of a comment that should be used to load the comment context using {@link
 * CommentContextCache#get(CommentContextKey)}, or {@link CommentContextCache#getAll(Iterable)}.
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

  /** Number of extra lines of context that should be added before and after the comment range. */
  abstract int contextPadding();

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

    public abstract Builder contextPadding(int numLines);

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
              .setContextPadding(key.contextPadding())
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
          .contextPadding(proto.getContextPadding())
          .build();
    }
  }
}
