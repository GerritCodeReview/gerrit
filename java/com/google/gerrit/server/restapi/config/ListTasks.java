// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.TaskInfo;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class ListTasks implements RestReadView<ConfigResource> {
  private final PermissionBackend permissionBackend;
  private final WorkQueue workQueue;
  private final Provider<CurrentUser> self;
  private final ProjectCache projectCache;

  @Inject
  public ListTasks(
      PermissionBackend permissionBackend,
      WorkQueue workQueue,
      Provider<CurrentUser> self,
      ProjectCache projectCache) {
    this.permissionBackend = permissionBackend;
    this.workQueue = workQueue;
    this.self = self;
    this.projectCache = projectCache;
  }

  @Override
  public Response<List<TaskInfo>> apply(ConfigResource resource)
      throws AuthException, PermissionBackendException {
    CurrentUser user = self.get();
    if (!user.isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    List<TaskInfo> allTasks = getTasks();
    if (permissionBackend.user(user).test(GlobalPermission.VIEW_QUEUE)) {
      return Response.ok(allTasks);
    }

    Map<String, Boolean> visibilityCache = new HashMap<>();
    List<TaskInfo> visibleTasks = new ArrayList<>();
    for (TaskInfo task : allTasks) {
      if (task.projectName != null) {
        Boolean visible = visibilityCache.get(task.projectName);
        if (visible == null) {
          Project.NameKey nameKey = Project.nameKey(task.projectName);
          Optional<ProjectState> state = projectCache.get(nameKey);
          if (!state.isPresent() || !state.get().statePermitsRead()) {
            visible = false;
          } else {
            if (permissionBackend.user(user).project(nameKey).test(ProjectPermission.ACCESS)) {
              visible = true;
            } else {
              visible = false;
            }
          }
          visibilityCache.put(task.projectName, visible);
        }
        if (visible) {
          visibleTasks.add(task);
        }
      }
    }
    return Response.ok(visibleTasks);
  }

  private List<TaskInfo> getTasks() {
    return workQueue.getTaskInfos(TaskInfo::new).stream()
        .sorted(
            comparing((TaskInfo t) -> t.state.ordinal())
                .thenComparing(t -> t.delay)
                .thenComparing(t -> t.command))
        .collect(toList());
  }
}
