// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.entities;

import static java.util.Objects.requireNonNull;

/** Stores together numeric {@link Change.Id} and a project name for the change */
public record ProjectChangeKey(Project.NameKey projectName, Change.Id changeId) {
  public ProjectChangeKey {
    requireNonNull(projectName, "projectName");
    requireNonNull(changeId, "changeId");
  }

  public static ProjectChangeKey create(Project.NameKey projectName, Change.Id changeId) {
    return new ProjectChangeKey(projectName, changeId);
  }

}
