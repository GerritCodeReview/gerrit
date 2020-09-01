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
//

package com.google.gerrit.server.patch.entities;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import java.io.Serializable;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.FileMode;

@AutoValue
public abstract class GitModifiedFile implements Serializable {
  public static GitModifiedFile create(
      @Nullable ChangeType changeType,
      String oldPath,
      String newPath,
      @Nullable FileMode oldMode,
      @Nullable FileMode newMode) {
    return new AutoValue_GitModifiedFile(changeType, oldPath, newPath, oldMode, newMode);
  }

  @Nullable
  public abstract ChangeType changeType();

  public abstract String oldPath();

  public abstract String newPath();

  @Nullable
  public abstract FileMode oldMode();

  @Nullable
  public abstract FileMode newMode();
}
