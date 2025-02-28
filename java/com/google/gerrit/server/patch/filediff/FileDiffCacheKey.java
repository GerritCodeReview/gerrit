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
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.FileDiffKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithm;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Cache key for the {@link FileDiffCache}.
 *
 * @param project A specific git project / repository.
 * @param oldCommit The 20 bytes SHA-1 commit ID of the old commit used in the diff. If set to
 *     {@link ObjectId#zeroId()}, an empty tree is used for the diff.
 * @param newCommit The 20 bytes SHA-1 commit ID of the new commit used in the diff.
 * @param newFilePath File path identified by its name.
 * @param renameScore Percentage score used to identify a file as a "rename". A special value of -1
 *     means that the computation will ignore renames and rename detection will be disabled.
 * @param diffAlgorithm The diff algorithm that should be used in the computation.
 * @param useTimeout Employ a timeout on the git computation while formatting the file header.
 */
public record FileDiffCacheKey(
    Project.NameKey project,
    ObjectId oldCommit,
    ObjectId newCommit,
    String newFilePath,
    int renameScore,
    DiffAlgorithm diffAlgorithm,
    DiffPreferencesInfo.Whitespace whitespace,
    boolean useTimeout) {
  public FileDiffCacheKey {
    requireNonNull(project, "project");
    requireNonNull(oldCommit, "oldCommit");
    requireNonNull(newCommit, "newCommit");
    requireNonNull(newFilePath, "newFilePath");
    requireNonNull(diffAlgorithm, "diffAlgorithm");
    requireNonNull(whitespace, "whitespace");
  }

  /** Number of bytes that this entity occupies. */
  public int weight() {
    return stringSize(project().get())
        + 20 * 2 // old and new commits
        + stringSize(newFilePath())
        + 4 // renameScore
        + 4 // diffAlgorithm
        + 4 // whitespace
        + 1; // useTimeout
  }

  public static FileDiffCacheKey.Builder builder() {
    return new AutoBuilder_FileDiffCacheKey_Builder();
  }

  public Builder toBuilder() {
    return new AutoBuilder_FileDiffCacheKey_Builder(this);
  }

  @AutoBuilder
  public abstract static class Builder {

    public abstract FileDiffCacheKey.Builder project(NameKey value);

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

    public abstract FileDiffCacheKey.Builder useTimeout(boolean value);

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
              .setUseTimeout(key.useTimeout())
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
          .useTimeout(proto.getUseTimeout())
          .build();
    }
  }
}
