// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.diff;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.data.PatchScript.PatchScriptFileInfo;

/**
 * Contains settings for one of two sides in diff view.
 * Each diff view has exactly 2 sides.
 */
@AutoValue
public abstract class DiffSide {
  public enum Type {
    SIDE_A,
    SIDE_B
  }

  public static DiffSide create(PatchScriptFileInfo fileInfo, String fileName, Type type) {
    return new AutoValue_DiffSide(fileInfo, fileName, type);
  }

  public abstract PatchScriptFileInfo fileInfo();
  public abstract String fileName();
  public abstract Type type();
}
