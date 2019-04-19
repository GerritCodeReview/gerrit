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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.ProjectTask;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.ioutil.HexFormat;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
  public List<TaskInfo> apply(ConfigResource resource)
      throws AuthException, PermissionBackendException {
    CurrentUser user = self.get();
    if (!user.isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    List<TaskInfo> allTasks = getTasks();
    try {
      permissionBackend.user(user).check(GlobalPermission.VIEW_QUEUE);
      return allTasks;
    } catch (AuthException e) {
      // Fall through to filter tasks.
    }

    Map<String, Boolean> visibilityCache = new HashMap<>();
    List<TaskInfo> visibleTasks = new ArrayList<>();
    for (TaskInfo task : allTasks) {
      if (task.projectName != null) {
        Boolean visible = visibilityCache.get(task.projectName);
        if (visible == null) {
          Project.NameKey nameKey = Project.nameKey(task.projectName);
          ProjectState state = projectCache.get(nameKey);
          if (state == null || !state.statePermitsRead()) {
            visible = false;
          } else {
            try {
              permissionBackend.user(user).project(nameKey).check(ProjectPermission.ACCESS);
              visible = true;
            } catch (AuthException e) {
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
    return visibleTasks;
  }

  private List<TaskInfo> getTasks() {
    return workQueue.getTaskInfos(TaskInfo::new).stream()
        .sorted(
            comparing((TaskInfo t) -> t.state.ordinal())
                .thenComparing(t -> t.delay)
                .thenComparing(t -> t.command))
        .collect(toList());
  }

  public static class TaskInfo {
    public String id;
    public Task.State state;
    public Timestamp startTime;
    public long delay;
    public String command;
    public String remoteName;
    public String projectName;
    public String queueName;

    public TaskInfo(Task<?> task) {
      this.id = HexFormat.fromInt(task.getTaskId());
      this.state = task.getState();
      this.startTime = new Timestamp(task.getStartTime().getTime());
      this.delay = task.getDelay(TimeUnit.MILLISECONDS);
      this.command = task.toString();
      this.queueName = task.getQueueName();

      if (task instanceof ProjectTask) {
        ProjectTask<?> projectTask = ((ProjectTask<?>) task);
        Project.NameKey name = projectTask.getProjectNameKey();
        if (name != null) {
          this.projectName = name.get();
        }
        this.remoteName = projectTask.getRemoteName();
      }
    }
  }
}
