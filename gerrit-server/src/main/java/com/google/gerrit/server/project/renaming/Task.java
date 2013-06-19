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
   * <p>
   * When implementing a project renaming task, declare an interface extending
   * {@code Task.Factory} within your implementation, and pass that interface
   * to {@code TaskModule} to register it.
   */
  public interface Factory {
    Task create(@Assisted("source") Project.NameKey source,
        @Assisted("destination") Project.NameKey destination);
  }

  /**
   * Priority of the task.
   * <p>
   * The lower the priority, the sooner the task is run during the renaming
   * process.
   *
   * <table border>
   * <tr><th>From</th><th>To</th><th>Description</th></tr>
   * <tr><td></td><td>19</td><td>No internal tasks have been run yet. The
   *    original project is still in it's original position, both in the
   *    database, and in the file system. The destination project has not yet
   *    been created.<p>Use a priority in this range if you want to run tasks
   *    before the renaming starts.</td></tr>
   * <tr><td>20</td><td>39</td><td>Reserved for gerrit's internal tasks.<p>
   *    Do not use this range in plugins/extensions.<p>
   *    In this range the destination project is created, and database
   *    migration happens.</td></tr>
   * <tr><td>40</td><td>59</td><td>The repository itself is still available
   *    under the source name. A stub for the destination project exists
   *    already, and all database rows already point to the destination
   *    project. The source repository is not referenced any longer. The
   *    destination repository does not yet exist in the file system.<p>Use a
   *    priority in this range if you rely on an updated database but still
   *    need to reference the repository at the original position in the file
   *    system.
   * <tr><td>60</td><td>79</td><td>Reserved for gerrit's internal tasks.<p>
   *    Do not use this range in plugins/extensions.<p>
   *    In this range, the repository gets moved from the source to the
   *    destination in the file system.</td></tr>
   * <tr><td>80</td><td></td><td>Project has successfully been renamed from
   *    source to destination. Use this range to trigger follow-up tasks.</td>
   *    </tr>
   * </table>
   */
  public int getPriority();

  /**
   * Carries out a project renaming step.
   * <p>
   * Note that if this method throws an error, {@code rollback} is not called
   * automatically for this step. In that case, {@code carryOut} is responsible
   * for rolling back the changes it has already done.
   *
   * @throws ProjectRenamingFailedException
   */
  public void carryOut() throws ProjectRenamingFailedException;

  /**
   * Rolls back a project renaming step.
   * <p>
   * This method is only called if one of the subsequent steps in the project
   * renaming procedure caused a failure. You should try as hard as possible
   * to undo what {@code carryOut} did.
   *
   * This function is not called if the current task caused problems.
   */
  public void rollback();
}
