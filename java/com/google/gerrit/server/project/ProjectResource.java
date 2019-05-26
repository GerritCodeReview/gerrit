// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.TypeLiteral;

public class ProjectResource implements RestResource {
  public static final TypeLiteral<RestView<ProjectResource>> PROJECT_KIND =
      new TypeLiteral<RestView<ProjectResource>>() {};

  private final ProjectState projectState;
  private final CurrentUser user;

  public ProjectResource(ProjectState projectState, CurrentUser user) {
    this.projectState = projectState;
    this.user = user;
  }

  ProjectResource(ProjectResource rsrc) {
    this.projectState = rsrc.getProjectState();
    this.user = rsrc.getUser();
  }

  public String getName() {
    return projectState.getName();
  }

  public Project.NameKey getNameKey() {
    return projectState.getNameKey();
  }

  public ProjectState getProjectState() {
    return projectState;
  }

  public CurrentUser getUser() {
    return user;
  }
}
