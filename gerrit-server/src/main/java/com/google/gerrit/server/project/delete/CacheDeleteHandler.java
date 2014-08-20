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

package com.google.gerrit.server.project.delete;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
public class CacheDeleteHandler {
  private final ProjectCache projectCache;

  @Inject
  public CacheDeleteHandler(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  public void delete(Project project) {
    projectCache.evict(project);
    projectCache.remove(project);
  }

  public void delete(Project.NameKey project) {
    ProjectState state = projectCache.get(project);
    if (state == null) {
      return;
    }
    projectCache.evict(state.getProject());
    projectCache.remove(state.getProject());
  }
}
