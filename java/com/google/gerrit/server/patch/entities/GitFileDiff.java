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
//

package com.google.gerrit.server.patch.entities;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch.ChangeType;
import java.io.Serializable;
import java.util.List;
import org.eclipse.jgit.diff.Edit;

@AutoValue
public abstract class GitFileDiff implements Serializable {
  public static GitFileDiff create(
      @Nullable List<Edit> edits,
      @Nullable FileHeader fileHeader,
      @Nullable String oldName,
      @Nullable String newName,
      @Nullable Long oldSize,
      @Nullable Long newSize) {
    return new AutoValue_GitFileDiff(edits, fileHeader, oldName, newName, oldSize, newSize);
  }

  public static GitFileDiff empty() {
    return new AutoValue_GitFileDiff(null, null, null, null, null, null);
  }

  @Nullable
  public abstract List<Edit> edits();

  @Nullable
  public abstract FileHeader fileHeader();

  @Nullable
  public abstract String oldName();

  @Nullable
  public abstract String newName();

  @Nullable
  public abstract Long oldSize();

  @Nullable
  public abstract Long newSize();

  public boolean isEmpty() {
    return edits() == null;
  }

  public ChangeType changeType() {
    return fileHeader().getChangeType();
  }
}
