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

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ParentProjectResolver {
  private final AllProjectsName allProjectsName;

  @Inject
  public ParentProjectResolver(final AllProjectsName allProjectsName) {
    this.allProjectsName = allProjectsName;
  }

  /**
   * Returns for the given project the name of the parent project.
   *
   * @param project the project for whose parent project the name should be
   *        returned
   * @return name of the parent project, <code>null</code> if the wild project
   *         was given
   */
  public Project.NameKey get(final Project project) {
    if (project.getParent() != null) {
      return project.getParent();
    }

    if (project.getNameKey().equals(allProjectsName)) {
      return null;
    }

    return allProjectsName;
  }
}
