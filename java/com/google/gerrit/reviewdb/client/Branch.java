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

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** Line of development within a {@link Project}. */
public final class Branch {
  /** Branch name key */
  public static class NameKey extends StringKey<Project.NameKey> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Project.NameKey projectName;

    @Column(id = 2)
    protected String branchName;

    protected NameKey() {
      projectName = new Project.NameKey();
    }

    public NameKey(Project.NameKey proj, String branchName) {
      projectName = proj;
      set(branchName);
    }

    public NameKey(String proj, String branchName) {
      this(new Project.NameKey(proj), branchName);
    }

    @Override
    public String get() {
      return branchName;
    }

    @Override
    protected void set(String newValue) {
      branchName = RefNames.fullName(newValue);
    }

    @Override
    public Project.NameKey getParentKey() {
      return projectName;
    }

    public String getShortName() {
      return RefNames.shortName(get());
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
    return name.get();
  }

  public String getShortName() {
    return name.getShortName();
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
