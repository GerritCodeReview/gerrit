// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.StringKey;

/** Registered line of development within a {@link Project}. */
public final class Branch {
  /** Branch name key */
  public static class NameKey extends StringKey<Project.NameKey> {
    @Column
    protected Project.NameKey projectName;

    @Column
    protected String branchName;

    protected NameKey() {
      projectName = new Project.NameKey();
    }

    public NameKey(final Project.NameKey proj, final String n) {
      projectName = proj;
      branchName = n;
    }

    @Override
    public String get() {
      return branchName;
    }

    @Override
    public Project.NameKey getParentKey() {
      return projectName;
    }

    public String getShortName() {
      final String n = get();

      // Git style branches will tend to start with "refs/heads/".
      // 
      if (n.startsWith("refs/heads/")) {
        return n.substring("refs/heads/".length());
      }

      return n;
    }
  }

  /** Synthetic key to link to within the database */
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    @Column
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }
  }

  @Column(name = Column.NONE)
  protected NameKey name;

  @Column
  protected Id branchId;

  protected Branch() {
  }

  public Branch(final Branch.NameKey newName, final Branch.Id newId) {
    name = newName;
    branchId = newId;
  }

  public Branch.Id getId() {
    return branchId;
  }

  public String getName() {
    return name.get();
  }

  public String getShortName() {
    return name.getShortName();
  }
}
