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

package com.google.gerrit.server.patch.filediff;

import static com.google.gerrit.server.patch.DiffUtil.stringSize;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.FileDiffKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithm;
import org.eclipse.jgit.lib.ObjectId;

/** Cache key for the {@link FileDiffCache}. */
@AutoValue
public abstract class FileDiffCacheKey {

  /** A specific git project / repository. */
  public abstract Project.NameKey project();

  /** The 20 bytes SHA-1 commit ID of the old commit used in the diff. */
  public abstract ObjectId oldCommit();

  /** The 20 bytes SHA-1 commit ID of the new commit used in the diff. */
  public abstract ObjectId newCommit();

  /** File path identified by its name. */
  public abstract String newFilePath();

  /**
   * Percentage score used to identify a file as a "rename". A special value of -1 means that the
   * computation will ignore renames and rename detection will be disabled.
   */
  public abstract int renameScore();

  /** The diff algorithm that should be used in the computation. */
  public abstract DiffAlgorithm diffAlgorithm();

  public abstract Whitespace whitespace();

  /** Number of bytes that this entity occupies. */
  public int weight() {
    return stringSize(project().get())
        + 20 * 2 // old and new commits
        + stringSize(newFilePath())
        + 4 // renameScore
        + 4 // diffAlgorithm
        + 4; // whitespace
  }

  public static FileDiffCacheKey.Builder builder() {
    return new AutoValue_FileDiffCacheKey.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract FileDiffCacheKey.Builder project(Project.NameKey value);

    public abstract FileDiffCacheKey.Builder oldCommit(ObjectId value);

    public abstract FileDiffCacheKey.Builder newCommit(ObjectId value);

    public abstract FileDiffCacheKey.Builder newFilePath(String value);

    public abstract FileDiffCacheKey.Builder renameScore(int value);

    public FileDiffCacheKey.Builder disableRenameDetection() {
      renameScore(-1);
      return this;
    }

    public abstract FileDiffCacheKey.Builder diffAlgorithm(DiffAlgorithm value);

    public abstract FileDiffCacheKey.Builder whitespace(Whitespace value);

    public abstract FileDiffCacheKey build();
  }

  public enum Serializer implements CacheSerializer<FileDiffCacheKey> {
    INSTANCE;

    @Override
    public byte[] serialize(FileDiffCacheKey key) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return Protos.toByteArray(
          FileDiffKeyProto.newBuilder()
              .setProject(key.project().get())
              .setOldCommit(idConverter.toByteString(key.oldCommit()))
              .setNewCommit(idConverter.toByteString(key.newCommit()))
              .setFilePath(key.newFilePath())
              .setRenameScore(key.renameScore())
              .setDiffAlgorithm(key.diffAlgorithm().name())
              .setWhitespace(key.whitespace().name())
              .build());
    }

    @Override
    public FileDiffCacheKey deserialize(byte[] in) {
      FileDiffKeyProto proto = Protos.parseUnchecked(FileDiffKeyProto.parser(), in);
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return FileDiffCacheKey.builder()
          .project(Project.nameKey(proto.getProject()))
          .oldCommit(idConverter.fromByteString(proto.getOldCommit()))
          .newCommit(idConverter.fromByteString(proto.getNewCommit()))
          .newFilePath(proto.getFilePath())
          .renameScore(proto.getRenameScore())
          .diffAlgorithm(DiffAlgorithm.valueOf(proto.getDiffAlgorithm()))
          .whitespace(Whitespace.valueOf(proto.getWhitespace()))
          .build();
    }
  }
}
