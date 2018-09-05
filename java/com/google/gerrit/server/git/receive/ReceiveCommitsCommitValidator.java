// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.git.receive;

import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

public class ReceiveCommitsCommitValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CommitValidators.Factory commitValidatorsFactory;
  private final IdentifiedUser user;
  private final PermissionBackend.ForProject permissions;
  private final Project project;
  private final SshInfo sshInfo;

  @AutoValue
  protected abstract static class ValidCommitKey {
    abstract ObjectId getObjectId();

    abstract Branch.NameKey getBranch();
  }

  private final Set<ReceiveCommitsCommitValidator.ValidCommitKey> validCommits;

  interface Factory {
    ReceiveCommitsCommitValidator create(ProjectState projectState, IdentifiedUser user);
  }

  @Inject
  ReceiveCommitsCommitValidator(
      CommitValidators.Factory commitValidatorsFactory,
      PermissionBackend permissionBackend,
      SshInfo sshInfo,
      @Assisted ProjectState projectState,
      @Assisted IdentifiedUser user) {
    this.sshInfo = sshInfo;
    this.user = user;
    this.commitValidatorsFactory = commitValidatorsFactory;
    project = projectState.getProject();
    permissions = permissionBackend.user(user).project(project.getNameKey());
    validCommits = new HashSet<>();
  }

  /**
   * Validates a single commit. If the commit does not validate, the command is rejected.
   *
   * @param objectReader the object reader to use.
   * @param branch the branch to which this commit is pushed
   * @param cmd the ReceiveCommand executing the push.
   * @param commit the commit being validated.
   * @param isMerged whether this is a merge commit created by magicBranch --merge option
   * @param change the change for which this is a new patchset.
   */
  public boolean validCommit(
      ObjectReader objectReader,
      Branch.NameKey branch,
      ReceiveCommand cmd,
      RevCommit commit,
      boolean isMerged,
      List<ValidationMessage> messages,
      NoteMap rejectCommits,
      @Nullable Change change)
      throws IOException {
    PermissionBackend.ForRef perm = permissions.ref(branch.get());

    ValidCommitKey key =
        new AutoValue_ReceiveCommitsCommitValidator_ValidCommitKey(commit.copy(), branch);
    if (validCommits.contains(key)) {
      return true;
    }

    try (CommitReceivedEvent receiveEvent =
        new CommitReceivedEvent(cmd, project, branch.get(), objectReader, commit, user)) {
      CommitValidators validators;
      if (isMerged) {
        validators =
            commitValidatorsFactory.forMergedCommits(
                project.getNameKey(), perm, user.asIdentifiedUser());
      } else {
        validators =
            commitValidatorsFactory.forReceiveCommits(
                perm,
                branch,
                user.asIdentifiedUser(),
                sshInfo,
                rejectCommits,
                receiveEvent.revWalk,
                change);
      }

      for (CommitValidationMessage m : validators.validate(receiveEvent)) {
        messages.add(
            new CommitValidationMessage(messageForCommit(commit, m.getMessage()), m.isError()));
      }
    } catch (CommitValidationException e) {
      logger.atFine().log("Commit validation failed on %s", commit.name());
      for (CommitValidationMessage m : e.getMessages()) {
        // The non-error messages may contain background explanation for the
        // fatal error, so have to preserve all messages.
        messages.add(
            new CommitValidationMessage(messageForCommit(commit, m.getMessage()), m.isError()));
      }
      cmd.setResult(REJECTED_OTHER_REASON, messageForCommit(commit, e.getMessage()));
      return false;
    }
    validCommits.add(key);
    return true;
  }

  private String messageForCommit(RevCommit c, String msg) {
    return String.format("commit %s: %s", c.abbreviate(RevId.ABBREV_LEN).name(), msg);
  }
}
