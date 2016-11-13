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

package com.google.gerrit.server.config;

import com.google.common.collect.ComparisonChain;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.TaskInfoFactory;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.ProjectTask;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.IdGenerator;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
public class ListTasks implements RestReadView<ConfigResource> {
  private final WorkQueue workQueue;
  private final ProjectCache projectCache;
  private final Provider<IdentifiedUser> self;

  @Inject
  public ListTasks(WorkQueue workQueue, ProjectCache projectCache, Provider<IdentifiedUser> self) {
    this.workQueue = workQueue;
    this.projectCache = projectCache;
    this.self = self;
  }

  @Override
  public List<TaskInfo> apply(ConfigResource resource) throws AuthException {
    CurrentUser user = self.get();
    if (!user.isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    List<TaskInfo> allTasks = getTasks();
    if (user.getCapabilities().canViewQueue()) {
      return allTasks;
    }
    Map<String, Boolean> visibilityCache = new HashMap<>();

    List<TaskInfo> visibleTasks = new ArrayList<>();
    for (TaskInfo task : allTasks) {
      if (task.projectName != null) {
        Boolean visible = visibilityCache.get(task.projectName);
        if (visible == null) {
          ProjectState e = projectCache.get(new Project.NameKey(task.projectName));
          visible = e != null ? e.controlFor(user).isVisible() : false;
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
    List<TaskInfo> taskInfos =
        workQueue.getTaskInfos(
            new TaskInfoFactory<TaskInfo>() {
              @Override
              public TaskInfo getTaskInfo(Task<?> task) {
                return new TaskInfo(task);
              }
            });
    Collections.sort(
        taskInfos,
        new Comparator<TaskInfo>() {
          @Override
          public int compare(TaskInfo a, TaskInfo b) {
            return ComparisonChain.start()
                .compare(a.state.ordinal(), b.state.ordinal())
                .compare(a.delay, b.delay)
                .compare(a.command, b.command)
                .result();
          }
        });
    return taskInfos;
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
      this.id = IdGenerator.format(task.getTaskId());
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
