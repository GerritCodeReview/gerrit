// Copyright (C) 2025 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.gerrit.common.ConvertibleToProto;
import com.google.gerrit.common.Nullable;

@AutoValue
@ConvertibleToProto
public abstract class ProjectWatchKey {

  public static ProjectWatchKey create(Project.NameKey project, @Nullable String filter) {
    return new AutoValue_ProjectWatchKey(project, Strings.emptyToNull(filter));
  }

  public abstract Project.NameKey project();

  public abstract @Nullable String filter();
}
