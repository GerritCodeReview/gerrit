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

import java.util.Objects;

/**
 * Defining a project/branch subscription to a project/branch project.
 *
 * <p>This means a class instance represents a repo/branch subscription to a project/branch (the
 * subscriber).
 *
 * <p>A subscriber operates a submodule in defined path.
 */
public final class SubmoduleSubscription {
  protected Branch.NameKey superProject;

  protected String submodulePath;

  protected Branch.NameKey submodule;

  public SubmoduleSubscription(Branch.NameKey superProject, Branch.NameKey submodule, String path) {
    this.superProject = superProject;
    this.submodule = submodule;
    this.submodulePath = path;
  }

  /**
   * Indicates the super project, aka subscriber: the project owner of the gitlinks to the
   * submodules.
   */
  public Branch.NameKey getSuperProject() {
    return superProject;
  }

  public String getPath() {
    return submodulePath;
  }

  public Branch.NameKey getSubmodule() {
    return submodule;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SubmoduleSubscription) {
      SubmoduleSubscription s = (SubmoduleSubscription) o;
      return superProject.equals(s.superProject)
          && submodulePath.equals(s.submodulePath)
          && submodule.equals(s.submodule);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(superProject, submodulePath, submodule);
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
