// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gerrit.server.IdentifiedUser;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Listener to Git extra validation step.
 *
 * Invoked by Gerrit when a new commit is received, has passed basic Gerrit
 * validation and can be then subject to extra validation checks.
 *
 */
@ExtensionPoint
public interface CommitValidatorListener {

  /**
   * Commit validation.
   *
   * @param command current Git command executed
   * @param project Gerrit project for the commit
   * @param refName target Git ref for the commit
   * @param commit Git commit
   * @param user user executing the Git command
   * @return validation result
   */
  public CommitValidationResult validate(final ReceiveCommand command,
      final Project project, final String refName, RevCommit commit,
      IdentifiedUser user);

}
