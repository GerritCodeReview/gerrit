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

package com.google.gerrit.server.patch.filediff;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Patch.PatchType;
import java.io.Serializable;
import java.util.Optional;

@AutoValue
public abstract class FileDiffOutput implements Serializable {
  public abstract Optional<String> oldPath();

  public abstract Optional<String> newPath();

  public abstract Patch.ChangeType changeType();

  public abstract Patch.PatchType patchType();

  public abstract ImmutableList<String> headerLines();

  public abstract ImmutableList<TaggedEdit> edits();

  public abstract long size();

  public abstract long sizeDelta();

  public boolean allEditsDueToRebase() {
    return !edits().isEmpty()
        && edits().stream().map(e -> e.dueToRebase()).allMatch(e -> e == true);
  }

  public int insertions() {
    int ins = 0;
    for (TaggedEdit e : edits()) {
      if (!e.dueToRebase()) {
        ins += e.edit().getEndB() - e.edit().getBeginB();
      }
    }
    return ins;
  }

  public int deletions() {
    int del = 0;
    for (TaggedEdit e : edits()) {
      if (!e.dueToRebase()) {
        del += e.edit().getEndA() - e.edit().getBeginA();
      }
    }
    return del;
  }

  static FileDiffOutput empty(String filePath) {
    return builder()
        .changeType(ChangeType.MODIFIED)
        .patchType(PatchType.UNIFIED)
        .oldPath(Optional.empty())
        .newPath(Optional.of(filePath))
        .headerLines(ImmutableList.of())
        .edits(ImmutableList.of())
        .size(0)
        .sizeDelta(0)
        .build();
  }

  public boolean isEmpty() {
    return headerLines().isEmpty();
  }

  static Builder builder() {
    return new AutoValue_FileDiffOutput.Builder();
  }

  public int weight() {
    // TODO(ghareeb): implement a proper weigher
    return 1;
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder oldPath(Optional<String> value);

    public abstract Builder newPath(Optional<String> value);

    public abstract Builder changeType(ChangeType value);

    public abstract Builder patchType(PatchType value);

    public abstract Builder headerLines(ImmutableList<String> value);

    public abstract Builder edits(ImmutableList<TaggedEdit> value);

    public abstract Builder size(long value);

    public abstract Builder sizeDelta(long value);

    public abstract FileDiffOutput build();
  }
}
