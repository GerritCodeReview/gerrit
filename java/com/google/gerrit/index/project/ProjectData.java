// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.index.project;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Project;

public class ProjectData {
  private final Project project;
  private final ImmutableList<Project.NameKey> ancestors;

  public ProjectData(Project project, Iterable<Project.NameKey> ancestors) {
    this.project = project;
    this.ancestors = ImmutableList.copyOf(ancestors);
  }

  public Project getProject() {
    return project;
  }

  public ImmutableList<Project.NameKey> getAncestors() {
    return ancestors;
  }
}
