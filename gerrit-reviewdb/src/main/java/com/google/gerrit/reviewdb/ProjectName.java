// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;

/** Unique name of an {@link Project}. */
public class ProjectName {
  @Column(id = 1)
  protected Project.NameKey name;

  @Column(id = 2)
  protected Project.Id projectId;

  protected ProjectName() {
  }

  public ProjectName(Project.NameKey name, Project.Id projectId) {
    this.name = name;
    this.projectId = projectId;
  }

  public ProjectName(Project project) {
    this(project.getNameKey(), project.getId());
  }

  public String getName() {
    return getNameKey().get();
  }

  public Project.NameKey getNameKey() {
    return name;
  }

  public Project.Id getId() {
    return projectId;
  }

  public void setId(Project.Id id) {
    projectId = id;
  }
}
