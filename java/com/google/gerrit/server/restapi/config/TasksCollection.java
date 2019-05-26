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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.TaskResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.ProjectTask;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class TasksCollection implements ChildCollection<ConfigResource, TaskResource> {
  private final DynamicMap<RestView<TaskResource>> views;
  private final ListTasks list;
  private final WorkQueue workQueue;
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;

  @Inject
  TasksCollection(
      DynamicMap<RestView<TaskResource>> views,
      ListTasks list,
      WorkQueue workQueue,
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      ProjectCache projectCache) {
    this.views = views;
    this.list = list;
    this.workQueue = workQueue;
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
  }

  @Override
  public RestView<ConfigResource> list() {
    return list;
  }

  @Override
  public TaskResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException, AuthException, PermissionBackendException,
          ResourceConflictException {
    CurrentUser user = self.get();
    if (!user.isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    int taskId;
    try {
      taskId = (int) Long.parseLong(id.get(), 16);
    } catch (NumberFormatException e) {
      throw new ResourceNotFoundException(id);
    }

    Task<?> task = workQueue.getTask(taskId);
    if (task instanceof ProjectTask) {
      Project.NameKey nameKey = ((ProjectTask<?>) task).getProjectNameKey();
      ProjectState state = projectCache.get(nameKey);
      if (state == null) {
        throw new ResourceNotFoundException(String.format("project %s not found", nameKey));
      }

      state.checkStatePermitsRead();

      try {
        permissionBackend.user(user).project(nameKey).check(ProjectPermission.ACCESS);
        return new TaskResource(task);
      } catch (AuthException e) {
        // Fall through and try view queue permission.
      }
    }

    if (task != null) {
      try {
        permissionBackend.user(user).check(GlobalPermission.VIEW_QUEUE);
        return new TaskResource(task);
      } catch (AuthException e) {
        // Fall through and return not found.
      }
    }

    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<TaskResource>> views() {
    return views;
  }
}
