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

package com.google.gerrit.server.patch.entities;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.server.patch.EditTransformer;
import java.util.List;
import org.eclipse.jgit.diff.Edit;

/**
 * An entity class containing the list of edits between 2 commits for a file, the old and new paths
 * of the paths and the change type. This entity is used as an input to {@link EditTransformer}
 * which computes the transformation of the edits from one git tree to another.
 */
@AutoValue
public abstract class FileEdits {
  public static FileEdits create(
      List<Edit> edits,
      @Nullable String oldPath,
      @Nullable String newPath,
      @Nullable ChangeType changeType) {
    return new AutoValue_FileEdits(edits, oldPath, newPath, changeType);
  }

  public abstract List<Edit> edits();

  @Nullable
  public abstract String oldPath();

  @Nullable
  public abstract String newPath();

  @Nullable
  public abstract ChangeType changeType();
}
