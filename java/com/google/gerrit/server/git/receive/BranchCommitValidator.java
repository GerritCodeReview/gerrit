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

import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.BranchNameKey;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Validates single commits for a branch. */
public class BranchCommitValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CommitValidators.Factory commitValidatorsFactory;
  private final IdentifiedUser user;
  private final PermissionBackend.ForProject permissions;
  private final Project project;
  private final BranchNameKey branch;
  private final SshInfo sshInfo;

  interface Factory {
    BranchCommitValidator create(
        ProjectState projectState, BranchNameKey branch, IdentifiedUser user);
  }

  /** A boolean validation status and a list of additional messages. */
  @AutoValue
  abstract static class Result {
    static Result create(boolean isValid, ImmutableList<CommitValidationMessage> messages) {
      return new AutoValue_BranchCommitValidator_Result(isValid, messages);
    }

    /** Whether the commit is valid. */
    abstract boolean isValid();

    /**
     * A list of messages related to the validation. Messages may be present regardless of the
     * {@link #isValid()} status.
     */
    abstract ImmutableList<CommitValidationMessage> messages();
  }

  @Inject
  BranchCommitValidator(
      CommitValidators.Factory commitValidatorsFactory,
      PermissionBackend permissionBackend,
      SshInfo sshInfo,
      @Assisted ProjectState projectState,
      @Assisted BranchNameKey branch,
      @Assisted IdentifiedUser user) {
    this.sshInfo = sshInfo;
    this.user = user;
    this.branch = branch;
    this.commitValidatorsFactory = commitValidatorsFactory;
    project = projectState.getProject();
    permissions = permissionBackend.user(user).project(project.getNameKey());
  }

  /**
   * Validates a single commit. If the commit does not validate, the command is rejected.
   *
   * @param objectReader the object reader to use.
   * @param cmd the ReceiveCommand executing the push.
   * @param commit the commit being validated.
   * @param isMerged whether this is a merge commit created by magicBranch --merge option
   * @param change the change for which this is a new patchset.
   * @return The validation {@link Result}.
   */
  Result validateCommit(
      ObjectReader objectReader,
      ReceiveCommand cmd,
      RevCommit commit,
      boolean isMerged,
      NoteMap rejectCommits,
      @Nullable Change change)
      throws IOException {
    return validateCommit(objectReader, cmd, commit, isMerged, rejectCommits, change, false);
  }

  /**
   * Validates a single commit. If the commit does not validate, the command is rejected.
   *
   * @param objectReader the object reader to use.
   * @param cmd the ReceiveCommand executing the push.
   * @param commit the commit being validated.
   * @param isMerged whether this is a merge commit created by magicBranch --merge option
   * @param change the change for which this is a new patchset.
   * @param skipValidation whether 'skip-validation' was requested.
   * @return The validation {@link Result}.
   */
  Result validateCommit(
      ObjectReader objectReader,
      ReceiveCommand cmd,
      RevCommit commit,
      boolean isMerged,
      NoteMap rejectCommits,
      @Nullable Change change,
      boolean skipValidation)
      throws IOException {
    ImmutableList.Builder<CommitValidationMessage> messages = new ImmutableList.Builder<>();
    try (CommitReceivedEvent receiveEvent =
        new CommitReceivedEvent(cmd, project, branch.branch(), objectReader, commit, user)) {
      CommitValidators validators;
      if (isMerged) {
        validators =
            commitValidatorsFactory.forMergedCommits(permissions, branch, user.asIdentifiedUser());
      } else {
        validators =
            commitValidatorsFactory.forReceiveCommits(
                permissions,
                branch,
                user.asIdentifiedUser(),
                sshInfo,
                rejectCommits,
                receiveEvent.revWalk,
                change,
                skipValidation);
      }

      for (CommitValidationMessage m : validators.validate(receiveEvent)) {
        messages.add(
            new CommitValidationMessage(
                messageForCommit(commit, m.getMessage(), objectReader), m.getType()));
      }
    } catch (CommitValidationException e) {
      logger.atFine().log("Commit validation failed on %s", commit.name());
      for (CommitValidationMessage m : e.getMessages()) {
        // The non-error messages may contain background explanation for the
        // fatal error, so have to preserve all messages.
        messages.add(
            new CommitValidationMessage(
                messageForCommit(commit, m.getMessage(), objectReader), m.getType()));
      }
      cmd.setResult(REJECTED_OTHER_REASON, messageForCommit(commit, e.getMessage(), objectReader));
      return Result.create(false, messages.build());
    }
    return Result.create(true, messages.build());
  }

  private String messageForCommit(RevCommit c, String msg, ObjectReader objectReader)
      throws IOException {
    return String.format("commit %s: %s", abbreviateName(c, objectReader), msg);
  }
}
