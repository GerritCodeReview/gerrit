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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Project;

public class ProjectData {

  private Project.NameKey nameKey;
  private String description;
  private int parentId;
  private String parentName;
  private int projectId;

  protected ProjectData() {
  }

  public ProjectData(final Project.NameKey nameKey, final String description,
      final int parentId, final String parentName, final int projectId) {
    this.nameKey = nameKey;
    this.description = description;
    this.parentId = parentId;
    this.parentName = parentName;
    this.projectId = projectId;
  }

  public Project.NameKey getNameKey() {
    return nameKey;
  }

  public String getName() {
    return nameKey.get();
  }

  public String getDescription() {
    return description;
  }

  public int getId() {
    return projectId;
  }

  public int getParentId() {
    return parentId;
  }

  public String getParentName() {
    return parentName;
  }
}
