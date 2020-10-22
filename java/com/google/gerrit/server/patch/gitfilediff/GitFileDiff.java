//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.patch.gitfilediff;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Patch.PatchType;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.entities.Weighable;
import java.util.Optional;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.AbbreviatedObjectId;

/**
 * Entity representing a modified file (added, deleted, modified, renamed, etc...) between two
 * different git commits.
 */
@AutoValue
public abstract class GitFileDiff implements Weighable {
  private static final String EMPTY = "";

  public static GitFileDiff empty() {
    return builder().edits(ImmutableList.of()).fileHeader(EMPTY).build();
  }

  public abstract ImmutableList<Edit> edits();

  public abstract String fileHeader();

  public abstract Optional<String> oldPath();

  public abstract Optional<String> newPath();

  public abstract Optional<AbbreviatedObjectId> oldId();

  public abstract Optional<AbbreviatedObjectId> newId();

  public abstract Optional<Patch.FileMode> oldMode();

  public abstract Optional<Patch.FileMode> newMode();

  @Nullable
  public abstract ChangeType changeType();

  @Nullable
  public abstract PatchType patchType();

  public boolean isEmpty() {
    return edits().isEmpty();
  }

  @Override
  public int weight() {
    // TODO(ghareeb): implement a proper weight method
    return 1;
  }

  public static Builder builder() {
    return new AutoValue_GitFileDiff.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder edits(ImmutableList<Edit> value);

    public abstract Builder fileHeader(String value);

    public abstract Builder oldPath(Optional<String> value);

    public abstract Builder newPath(Optional<String> value);

    public abstract Builder oldId(Optional<AbbreviatedObjectId> value);

    public abstract Builder newId(Optional<AbbreviatedObjectId> value);

    public abstract Builder oldMode(Optional<Patch.FileMode> value);

    public abstract Builder newMode(Optional<Patch.FileMode> value);

    public abstract Builder changeType(ChangeType value);

    public abstract Builder patchType(PatchType value);

    public abstract GitFileDiff build();
  }

  enum Serializer implements CacheSerializer<GitFileDiff> {
    INSTANCE;

    @Override
    public byte[] serialize(GitFileDiff object) {
      // TODO(ghareeb)
      return new byte[0];
    }

    @Override
    public GitFileDiff deserialize(byte[] in) {
      // TODO(ghareeb)
      return null;
    }
  }
}
