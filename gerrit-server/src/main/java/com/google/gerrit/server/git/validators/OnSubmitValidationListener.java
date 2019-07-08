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
import com.google.gerrit.server.validators.ValidationException;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

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
    private Repository repository;
    private ObjectReader objectReader;
    private Map<String, ReceiveCommand> commands;

    public Arguments(
        NameKey project,
        Repository repository,
        ObjectReader objectReader,
        Map<String, ReceiveCommand> commands) {
      this.project = project;
      this.repository = repository;
      this.objectReader = objectReader;
      this.commands = commands;
    }

    public Project.NameKey getProject() {
      return project;
    }

    /** @return a read only repository */
    public Repository getRepository() {
      return repository;
    }

    public RevWalk newRevWalk() {
      return new RevWalk(objectReader);
    }

    /**
     * @return a map from ref to op on it covering all ref ops to be performed on this repository as
     *     part of ongoing submit operation.
     */
    public Map<String, ReceiveCommand> getCommands() {
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
