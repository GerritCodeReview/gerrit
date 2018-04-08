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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetParent implements RestReadView<ProjectResource> {
  private final AllProjectsName allProjectsName;

  @Inject
  GetParent(AllProjectsName allProjectsName) {
    this.allProjectsName = allProjectsName;
  }

  @Override
  public String apply(ProjectResource resource) {
    Project project = resource.getProjectState().getProject();
    Project.NameKey parentName = project.getParent(allProjectsName);
    return parentName != null ? parentName.get() : "";
  }
}
