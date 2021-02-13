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

package com.google.gerrit.server.patch.gitdiff;

import static com.google.gerrit.server.patch.DiffUtil.stringSize;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.GitModifiedFilesKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.patch.DiffUtil;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

/** Cache key for the {@link GitModifiedFilesCache}. */
@AutoValue
public abstract class GitModifiedFilesCacheKey {

  /** A specific git project / repository. */
  public abstract Project.NameKey project();

  /**
   * The git SHA-1 {@link ObjectId} of the first git tree object for which the diff should be
   * computed.
   */
  public abstract ObjectId aTree();

  /**
   * The git SHA-1 {@link ObjectId} of the second git tree object for which the diff should be
   * computed.
   */
  public abstract ObjectId bTree();

  /**
   * Percentage score used to identify a file as a rename. This value is only available if {@link
   * #renameDetection()} is true. Otherwise, this method will return -1.
   *
   * <p>This value will be used to set the rename score of {@link
   * org.eclipse.jgit.diff.DiffFormatter#getRenameDetector()}.
   */
  public abstract int renameScore();

  /** Returns true if rename detection was set for this key. */
  public boolean renameDetection() {
    return renameScore() != -1;
  }

  public static GitModifiedFilesCacheKey create(
      Project.NameKey project, ObjectId aCommit, ObjectId bCommit, int renameScore, RevWalk rw)
      throws IOException {
    ObjectId aTree = DiffUtil.getTreeId(rw, aCommit);
    ObjectId bTree = DiffUtil.getTreeId(rw, bCommit);
    return builder().project(project).aTree(aTree).bTree(bTree).renameScore(renameScore).build();
  }

  public static Builder builder() {
    return new AutoValue_GitModifiedFilesCacheKey.Builder();
  }

  /** Returns the size of the object in bytes */
  public int weight() {
    return stringSize(project().get())
        + 20 * 2 // old and new tree IDs
        + 4; // rename score
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder project(Project.NameKey value);

    public abstract Builder aTree(ObjectId value);

    public abstract Builder bTree(ObjectId value);

    public abstract Builder renameScore(int value);

    public Builder disableRenameDetection() {
      renameScore(-1);
      return this;
    }

    public abstract GitModifiedFilesCacheKey build();
  }

  public enum Serializer implements CacheSerializer<GitModifiedFilesCacheKey> {
    INSTANCE;

    @Override
    public byte[] serialize(GitModifiedFilesCacheKey key) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return Protos.toByteArray(
          GitModifiedFilesKeyProto.newBuilder()
              .setProject(key.project().get())
              .setATree(idConverter.toByteString(key.aTree()))
              .setBTree(idConverter.toByteString(key.bTree()))
              .setRenameScore(key.renameScore())
              .build());
    }

    @Override
    public GitModifiedFilesCacheKey deserialize(byte[] in) {
      GitModifiedFilesKeyProto proto = Protos.parseUnchecked(GitModifiedFilesKeyProto.parser(), in);
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return GitModifiedFilesCacheKey.builder()
          .project(Project.NameKey.parse(proto.getProject()))
          .aTree(idConverter.fromByteString(proto.getATree()))
          .bTree(idConverter.fromByteString(proto.getBTree()))
          .renameScore(proto.getRenameScore())
          .build();
    }
  }
}
