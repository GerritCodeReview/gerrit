// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/** Used to retrieve the project name from an operation * */
public interface ProjectRunnable extends Runnable {
  Project.NameKey getProjectNameKey();

  @Nullable
  default String getRemoteName() {
    return null;
  }

  default boolean hasCustomizedPrint() {
    return false;
  }

  /**
   * Wraps the callable as a {@link FutureTask} and makes it comply with the {@link ProjectRunnable}
   * interface.
   */
  static <T> FutureTask<T> fromCallable(
      Callable<T> callable, Project.NameKey projectName, String operationName) {
    return new FromCallable<>(callable, projectName, operationName);
  }

  class FromCallable<T> extends FutureTask<T> implements ProjectRunnable {
    private final Project.NameKey project;
    private final String operationName;

    FromCallable(Callable<T> callable, Project.NameKey project, String operationName) {
      super(callable);
      this.project = project;
      this.operationName = operationName;
    }

    @Override
    public Project.NameKey getProjectNameKey() {
      return project;
    }

    @Override
    public String toString() {
      return operationName;
    }
  }
}
