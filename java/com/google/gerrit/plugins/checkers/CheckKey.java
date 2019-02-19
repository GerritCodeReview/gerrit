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

package com.google.gerrit.plugins.checkers;

import com.google.auto.value.AutoValue;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;

/** Fields to identify a check. */
@AutoValue
public abstract class CheckKey {
  public abstract Project.NameKey project();

  public abstract PatchSet.Id patchSet();

  public abstract String checkerUUID();

  public static CheckKey create(Project.NameKey project, PatchSet.Id patchSet, String checkerUUID) {
    return new AutoValue_CheckKey(project, patchSet, checkerUUID);
  }
}
