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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/**
 * Defining a project/branch subscription to a project/branch project.
 *
 * <p>This means a class instance represents a repo/branch subscription to a project/branch (the
 * subscriber).
 *
 * <p>A subscriber operates a submodule in defined path.
 */
public final class SubmoduleSubscription {
  /** Subscription key */
  public static class Key extends StringKey<Branch.NameKey> {
    private static final long serialVersionUID = 1L;

    /**
     * Indicates the super project, aka subscriber: the project owner of the gitlinks to the
     * submodules.
     */
    @Column(id = 1)
    protected Branch.NameKey superProject;

    @Column(id = 2)
    protected String submodulePath;

    protected Key() {
      superProject = new Branch.NameKey();
    }

    protected Key(Branch.NameKey superProject, String path) {
      this.superProject = superProject;
      this.submodulePath = path;
    }

    @Override
    public Branch.NameKey getParentKey() {
      return superProject;
    }

    @Override
    public String get() {
      return submodulePath;
    }

    @Override
    protected void set(String newValue) {
      this.submodulePath = newValue;
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected Branch.NameKey submodule;

  protected SubmoduleSubscription() {}

  public SubmoduleSubscription(Branch.NameKey superProject, Branch.NameKey submodule, String path) {
    this.key = new Key(superProject, path);
    this.submodule = submodule;
  }

  public Key getKey() {
    return key;
  }

  public Branch.NameKey getSuperProject() {
    return key.superProject;
  }

  public String getPath() {
    return key.get();
  }

  public Branch.NameKey getSubmodule() {
    return submodule;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SubmoduleSubscription) {
      return key.equals(((SubmoduleSubscription) o).key)
          && submodule.equals(((SubmoduleSubscription) o).submodule);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getSuperProject()).append(':').append(getPath());
    sb.append(" follows ");
    sb.append(getSubmodule());
    return sb.toString();
  }
}
