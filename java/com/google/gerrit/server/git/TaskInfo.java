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

package com.google.gerrit.server.git;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.WorkQueue.ProjectTask;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.ioutil.HexFormat;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

public class TaskInfo {
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
    this.startTime = Timestamp.from(task.getStartTime());
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
