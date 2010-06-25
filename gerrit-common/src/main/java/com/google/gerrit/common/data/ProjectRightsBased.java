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

/**
 * Additional data about a Project: defines a project based on its rights, if
 * it is visible or not to logged user.
 */
public class ProjectRightsBased {

  private ProjectData projectData;
  private boolean visible;

  /**
   * Creates an instance of a project with data about if it is visible or not.
   */
  protected ProjectRightsBased() {
  }

  /**
   * Creates an instance of a project with data about if it is visible or not.
   * @param project Project.
   * @param visible If project is visible or not.
   */
  public ProjectRightsBased(ProjectData projectData, boolean visible) {
    this.projectData = projectData;
    this.visible = visible;
  }

  public ProjectData getProjectData() {
    return projectData;
  }

  public boolean isVisible() {
    return visible;
  }
}
