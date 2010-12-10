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
import com.google.gerrit.reviewdb.Project.Status;

public class ProjectData {
  private Project.NameKey nameKey;
  private String description;
  private Status status;
  private boolean isEmpty;
  private boolean canBePruned;
  private boolean canBeUpdated;

  protected ProjectData() {
  }

  public ProjectData(final Project.NameKey nameKey, final String description,
      final boolean isEmpty, final boolean canBeUpdated,
      final boolean canBeDeleted, final Status status) {
    this.nameKey = nameKey;
    this.description = description;
    this.isEmpty = isEmpty;
    this.canBePruned = canBeDeleted;
    this.canBeUpdated = canBeUpdated;
    this.status = status;
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

  public boolean isEmpty() {
    return isEmpty;
  }

  public boolean canBePruned() {
    return canBePruned;
  }

  public boolean canBeUpdated() {
    return canBeUpdated;
  }

  public void setStatus(final Status status) {
    this.status = status;
  }

  public Status getStatus() {
    return status;
  }
}
