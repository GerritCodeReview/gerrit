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

package com.google.gerrit.server.patch.diff;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheKey;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class ModifiedFilesCacheKey {
  public abstract Project.NameKey project();

  /** @return the old commit ID used in the git tree diff */
  public abstract ObjectId aCommit();

  /** @return the new commit ID used in the git tree diff */
  public abstract ObjectId bCommit();

  /**
   * Percentage score used to identify a file as a "rename". A special value of -1 means that the
   * computation will ignore renames and rename detection will be disabled.
   */
  public abstract int renameScore();

  public boolean renameDetectionEnabled() {
    return renameScore() != -1;
  }

  public int weight() {
    return project().get().length() + 20 * 2 + 4;
  }

  public static ModifiedFilesCacheKey.Builder builder() {
    return new AutoValue_ModifiedFilesCacheKey.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract ModifiedFilesCacheKey.Builder project(NameKey value);

    public abstract ModifiedFilesCacheKey.Builder aCommit(ObjectId value);

    public abstract ModifiedFilesCacheKey.Builder bCommit(ObjectId value);

    public ModifiedFilesCacheKey.Builder disableRenameDetection() {
      renameScore(-1);
      return this;
    }

    public abstract ModifiedFilesCacheKey.Builder renameScore(int value);

    public abstract ModifiedFilesCacheKey build();
  }

  public enum Serializer implements CacheSerializer<ModifiedFilesCacheKey> {
    INSTANCE;

    @Override
    public byte[] serialize(ModifiedFilesCacheKey key) {
      // We are reusing the serializer of the GitModifiedFilesCacheImpl#Key since both classes
      // contain exactly the same fields, with the difference that the Object Ids here refer
      // to the commit SHA-1s instead of the tree SHA-1s, but they are still can be serialized
      // and deserialized in the same way.
      GitModifiedFilesCacheKey gitKey =
          GitModifiedFilesCacheKey.builder()
              .project(key.project())
              .aTree(key.aCommit())
              .bTree(key.bCommit())
              .renameScore(key.renameScore())
              .build();

      return GitModifiedFilesCacheKey.Serializer.INSTANCE.serialize(gitKey);
    }

    @Override
    public ModifiedFilesCacheKey deserialize(byte[] in) {
      GitModifiedFilesCacheKey gitKey =
          GitModifiedFilesCacheKey.Serializer.INSTANCE.deserialize(in);
      return ModifiedFilesCacheKey.builder()
          .project(gitKey.project())
          .aCommit(gitKey.aTree())
          .bCommit(gitKey.bTree())
          .renameScore(gitKey.renameScore())
          .build();
    }
  }
}
