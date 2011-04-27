// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gwtorm.client.CompoundKey;

/** Parents are simply other Projects */
public final class ProjectParent {
  /** ProjectParent key */
  public static class Key extends CompoundKey<Project.NameKey> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Project.NameKey project;

    @Column(id = 2)
    protected Project.NameKey parent;

    protected Key() {
      this(new Project.NameKey(), new Project.NameKey());
    }

    public Key(final Project.NameKey project, final Project.NameKey parent) {
      setProjectKey(project);
      setParentKey(parent);
    }

    public Project.NameKey getProjectKey() {
      return project;
    }

    protected void setProjectKey(final Project.NameKey project) {
      if (project == null) {
        throw new NullPointerException();
      }
      this.project = project;
    }

    @Override
    public Project.NameKey getParentKey() {
      return parent;
    }

    public void setParentKey(final Project.NameKey parent) {
      if (parent == null) {
        throw new NullPointerException();
      }
      this.parent = parent;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {project, parent};
    }

    @Override
    public boolean equals(final Object o) {
      if (o instanceof ProjectParent.Key) {
        ProjectParent.Key other = (ProjectParent.Key) o;
        return getProjectKey().equals(other.getProjectKey()) &&
               getParentKey().equals(other.getParentKey());
      }
      return false;
    }
  }

  @Column(id = 1)
  protected Key key;

  protected ProjectParent() {
  }

  public ProjectParent(final Project.NameKey project,
      final Project.NameKey parent) {
    key = new Key(project, parent);
  }

  public Project.NameKey getParentKey() {
    return key.getParentKey();
  }

  public Project.NameKey getProjectKey() {
    return key.getProjectKey();
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof ProjectParent) {
      ProjectParent other = (ProjectParent) o;
      return key.equals(other.key);
    }
    return false;
  }
}
