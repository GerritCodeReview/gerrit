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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.git.RefCache;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import com.google.gerrit.server.validators.ValidationException;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
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
    private RevWalk rw;
    private ImmutableMap<String, ReceiveCommand> commands;
    private RefCache refs;

    /**
     * @param project project.
     * @param rw revwalk that can read unflushed objects from {@code refs}.
     * @param commands commands to be executed.
     */
    Arguments(Project.NameKey project, RevWalk rw, ChainedReceiveCommands commands) {
      this.project = checkNotNull(project);
      this.rw = checkNotNull(rw);
      this.refs = checkNotNull(commands);
      this.commands = ImmutableMap.copyOf(commands.getCommands());
    }

    /** Get the project name for this operation. */
    public Project.NameKey getProject() {
      return project;
    }

    /**
     * Get a revwalk for this operation.
     *
     * <p>This instance is able to read all objects mentioned in {@link #getCommands()} and {@link
     * #getRef(String)}.
     *
     * @return open revwalk.
     */
    public RevWalk getRevWalk() {
      return rw;
    }

    /**
     * @return a map from ref to commands covering all ref operations to be performed on this
     *     repository as part of the ongoing submit operation.
     */
    public ImmutableMap<String, ReceiveCommand> getCommands() {
      return commands;
    }

    /**
     * Get a ref from the repository.
     *
     * @param name ref name; can be any ref, not just the ones mentioned in {@link #getCommands()}.
     * @return latest value of a ref in the repository, as if all commands from {@link
     *     #getCommands()} had already been applied.
     * @throws IOException if an error occurred reading the ref.
     */
    public Optional<ObjectId> getRef(String name) throws IOException {
      return refs.get(name);
    }
  }

  /**
   * Called right before branch is updated with new commit or commits as a result of submit.
   *
   * <p>If ValidationException is thrown, submitting is aborted.
   */
  void preBranchUpdate(Arguments args) throws ValidationException;
}
