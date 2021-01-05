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

import static com.google.gerrit.server.patch.DiffUtil.stringSize;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Patch.PatchType;
import java.io.Serializable;
import java.util.Optional;

/** File diff for a single file path. Produced as output of the {@link FileDiffCache}. */
@AutoValue
public abstract class FileDiffOutput implements Serializable {

  /**
   * The file path at the old commit. Returns an empty Optional if {@link #changeType()} is equal to
   * {@link ChangeType#ADDED}.
   */
  public abstract Optional<String> oldPath();

  /**
   * The file path at the new commit. Returns an empty optional if {@link #changeType()} is equal to
   * {@link ChangeType#DELETED}.
   */
  public abstract Optional<String> newPath();

  /** The change type of the underlying file, e.g. added, deleted, renamed, etc... */
  public abstract Optional<Patch.ChangeType> changeType();

  /** The patch type of the underlying file, e.g. unified, binary , etc... */
  public abstract Optional<Patch.PatchType> patchType();

  /**
   * A list of strings representation of the header lines of the {@link
   * org.eclipse.jgit.patch.FileHeader} that is produced as output of the diff.
   */
  public abstract ImmutableList<String> headerLines();

  /** The list of edits resulting from the diff hunks of the file. */
  public abstract ImmutableList<TaggedEdit> edits();

  /** The file size at the new commit. */
  public abstract long size();

  /** Difference in file size between the old and new commits. */
  public abstract long sizeDelta();

  /** A boolean indicating if all underlying edits of the file diff are due to rebase. */
  public boolean allEditsDueToRebase() {
    return !edits().isEmpty() && edits().stream().allMatch(TaggedEdit::dueToRebase);
  }

  /** Returns the number of inserted lines for the file diff. */
  public int insertions() {
    int ins = 0;
    for (TaggedEdit e : edits()) {
      if (!e.dueToRebase()) {
        ins += e.edit().endB() - e.edit().beginB();
      }
    }
    return ins;
  }

  /** Returns the number of deleted lines for the file diff. */
  public int deletions() {
    int del = 0;
    for (TaggedEdit e : edits()) {
      if (!e.dueToRebase()) {
        del += e.edit().endA() - e.edit().beginA();
      }
    }
    return del;
  }

  /** Returns an entity representing an unchanged file between two commits. */
  static FileDiffOutput empty(String filePath) {
    return builder()
        .oldPath(Optional.empty())
        .newPath(Optional.of(filePath))
        .headerLines(ImmutableList.of())
        .edits(ImmutableList.of())
        .size(0)
        .sizeDelta(0)
        .build();
  }

  /** Returns true if this entity represents an unchanged file between two commits. */
  public boolean isEmpty() {
    return headerLines().isEmpty() && edits().isEmpty();
  }

  static Builder builder() {
    return new AutoValue_FileDiffOutput.Builder();
  }

  public int weight() {
    int result = 0;
    if (oldPath().isPresent()) {
      result += stringSize(oldPath().get());
    }
    if (newPath().isPresent()) {
      result += stringSize(newPath().get());
    }
    if (changeType().isPresent()) {
      result += 4;
    }
    if (patchType().isPresent()) {
      result += 4;
    }
    result += 4 + 4; // insertions and deletions
    result += 4 + 4; // size and size delta
    result += 20 * edits().size(); // each edit is 4 Integers + boolean = 4 * 4 + 4 = 20
    for (String s : headerLines()) {
      s += stringSize(s);
    }
    return result;
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder oldPath(Optional<String> value);

    public abstract Builder newPath(Optional<String> value);

    public abstract Builder changeType(Optional<ChangeType> value);

    public abstract Builder patchType(Optional<PatchType> value);

    public abstract Builder headerLines(ImmutableList<String> value);

    public abstract Builder edits(ImmutableList<TaggedEdit> value);

    public abstract Builder size(long value);

    public abstract Builder sizeDelta(long value);

    public abstract FileDiffOutput build();
  }
}
