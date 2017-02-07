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
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import java.util.HashMap;
import java.util.Map;

/** Caches {@link ProjectControl} objects for the current user of the request. */
@RequestScoped
public class PerRequestProjectControlCache {
  private final ProjectCache projectCache;
  private final CurrentUser user;
  private final Map<Project.NameKey, ProjectControl> controls;

  @Inject
  PerRequestProjectControlCache(ProjectCache projectCache, CurrentUser userProvider) {
    this.projectCache = projectCache;
    this.user = userProvider;
    this.controls = new HashMap<>();
  }

  ProjectControl get(Project.NameKey nameKey) throws NoSuchProjectException {
    ProjectControl ctl = controls.get(nameKey);
    if (ctl == null) {
      ProjectState p = projectCache.get(nameKey);
      if (p == null) {
        throw new NoSuchProjectException(nameKey);
      }
      ctl = p.controlFor(user);
      controls.put(nameKey, ctl);
    }
    return ctl;
  }

  public void evict(Project project) {
    projectCache.evict(project);
    controls.remove(project.getNameKey());
  }
}
