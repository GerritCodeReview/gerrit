// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import com.google.gerrit.server.validators.ValidationException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Listener to validate ref updates performed during submit operation.
 *
 * <p>As submit strategies may generate new commits (e.g. Cherry Pick), this listener allows
 * validation of resulting new commit before destination branch is updated and new patchset ref is
 * created.
 *
 * <p>If you only care about validating the change being submitted and not the resulting new commit,
 * consider using {@link MergeValidationListener} instead.
 */
@ExtensionPoint
public interface OnSubmitValidationListener {
  class Arguments {
    private Project.NameKey project;
    private ObjectReader objectReader;
    private ChainedReceiveCommands commands;

    public Arguments(NameKey project, ObjectReader objectReader, ChainedReceiveCommands commands) {
      this.project = project;
      this.objectReader = objectReader;
      this.commands = commands;
    }

    public Project.NameKey getProject() {
      return project;
    }

    public RevWalk newRevWalk() {
      return new RevWalk(objectReader);
    }

    /**
     * Get pending commands that will be executed.
     *
     * <p>The returned instance can be used to inspect the commands that are going to be executed,
     * and can also read the values of any ref in the repo, taking into account the results of the
     * new commands.
     *
     * <p>Objects referenced by pending commands are guaranteed to be readable by the result of
     * {@link #newRevWalk()}.
     *
     * @return pending commands.
     */
    public ChainedReceiveCommands getCommands() {
      return commands;
    }
  }

  /**
   * Called right before branch is updated with new commit or commits as a result of submit.
   *
   * <p>If ValidationException is thrown, submitting is aborted.
   */
  void preBranchUpdate(Arguments args) throws ValidationException;
}
