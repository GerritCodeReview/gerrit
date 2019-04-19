// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import com.google.auto.value.AutoValue;
import com.google.gwtorm.client.StandardKeyEncoder;

/** Line of development within a {@link Project}. */
public final class Branch {
  public static NameKey nameKey(Project.NameKey projectName, String branchName) {
    return new AutoValue_Branch_NameKey(projectName, RefNames.fullName(branchName));
  }

  public static NameKey nameKey(String projectName, String branchName) {
    return nameKey(Project.nameKey(projectName), branchName);
  }

  /** Branch name key */
  @AutoValue
  public abstract static class NameKey implements Comparable<NameKey> {
    public abstract Project.NameKey project();

    public abstract String branch();

    public String shortName() {
      return RefNames.shortName(branch());
    }

    @Override
    public int compareTo(NameKey o) {
      // TODO(dborowitz): Only compares branch name in order to match old StringKey behavior.
      // Consider comparing project name first.
      return branch().compareTo(o.branch());
    }

    @Override
    public String toString() {
      return project() + "," + new StandardKeyEncoder().encode(branch());
    }
  }

  protected NameKey name;
  protected RevId revision;
  protected boolean canDelete;

  protected Branch() {}

  public Branch(Branch.NameKey newName) {
    name = newName;
  }

  public Branch.NameKey getNameKey() {
    return name;
  }

  public String getName() {
    return name.branch();
  }

  public String getShortName() {
    return name.shortName();
  }

  public RevId getRevision() {
    return revision;
  }

  public void setRevision(RevId id) {
    revision = id;
  }

  public boolean getCanDelete() {
    return canDelete;
  }

  public void setCanDelete(boolean canDelete) {
    this.canDelete = canDelete;
  }
}
