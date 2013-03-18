// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.project.ProjectJson.ProjectInfo;
import com.google.inject.Inject;

import java.util.List;

public class ListChildProjects implements RestReadView<ProjectResource> {

  private final ProjectCache projectCache;
  private final AllProjectsName allProjects;
  private final ProjectJson json;

  @Inject
  ListChildProjects(ProjectCache projectCache, AllProjectsName allProjects,
      ProjectJson json) {
    this.projectCache = projectCache;
    this.allProjects = allProjects;
    this.json = json;
  }

  @Override
  public List<ProjectInfo> apply(ProjectResource rsrc) {
    List<ProjectInfo> childProjects = Lists.newArrayList();
    for (Project.NameKey projectName : projectCache.all()) {
      ProjectState e = projectCache.get(projectName);
      if (e == null) {
        // If we can't get it from the cache, pretend it's not present.
        continue;
      }
      if (rsrc.getNameKey().equals(e.getProject().getParent(allProjects))) {
        childProjects.add(json.format(e.getProject()));
      }
    }
    return childProjects;
  }
}
