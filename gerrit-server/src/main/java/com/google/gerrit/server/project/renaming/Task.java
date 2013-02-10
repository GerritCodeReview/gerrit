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

package com.google.gerrit.server.project.renaming;

import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.assistedinject.Assisted;

/**
 * Treats one of the required tasks to rename a project.
 */
public interface Task {

  /**
   * Interface for factories for a project renaming task
   *
   * When implementing a project renaming task, declare an interface extending
   * {@code Task.Factory} within your implementation, and pass that interface
   * to {@code TaskModule} to register it.
   */
  public interface Factory {
    Task create(@Assisted("source") Project.NameKey source,
        @Assisted("destination") Project.NameKey destination);
  }

  /**
   * Carries out a project renaming step.
   *
   * Note that if this method throws an error, {@code rollback} is not called
   * automatically for this step.
   *
   * @throws ProjectRenamingFailedException
   */
  public void carryOut() throws ProjectRenamingFailedException;

  /**
   * Rolls back a project renaming step.
   *
   * This method is only called if one of the subsequent steps in the project
   * renaming procedure caused a failure. You should try as hard as possible
   * to undo what {@code carryOut} did.
   *
   * This function is not called if the current task caused problems.
   */
  public void rollback();
}
