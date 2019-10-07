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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProjectData {
  private final Project project;
  private final Optional<ProjectData> parent;

  public ProjectData(Project project, Optional<ProjectData> parent) {
    this.project = project;
    this.parent = parent;
  }

  public Project getProject() {
    return project;
  }

  public Optional<ProjectData> getParent() {
    return parent;
  }

  /** Returns all {@link ProjectData} in the hierarchy starting with the current one. */
  public ImmutableList<ProjectData> tree() {
    List<ProjectData> parents = new ArrayList<>();
    Optional<ProjectData> curr = Optional.of(this);
    while (curr.isPresent()) {
      parents.add(curr.get());
      curr = curr.get().parent;
    }
    return ImmutableList.copyOf(parents);
  }

  public ImmutableList<String> getParentNames() {
    return tree().stream().skip(1).map(p -> p.getProject().getName()).collect(toImmutableList());
  }

  @Override
  public String toString() {
    MoreObjects.ToStringHelper h = MoreObjects.toStringHelper(this);
    h.addValue(project.getName());
    return h.toString();
  }
}
