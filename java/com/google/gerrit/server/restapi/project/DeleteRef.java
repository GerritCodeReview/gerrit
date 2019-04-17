// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.reviewdb.client.RefNames.isConfigRef;
import static java.lang.String.format;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefValidationHelper;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

@Singleton
public class DeleteRef {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int MAX_LOCK_FAILURE_CALLS = 10;
  private static final long SLEEP_ON_LOCK_FAILURE_MS = 15;

  private final Provider<IdentifiedUser> identifiedUser;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated referenceUpdated;
  private final RefValidationHelper refDeletionValidator;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  DeleteRef(
      Provider<IdentifiedUser> identifiedUser,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      GitReferenceUpdated referenceUpdated,
      RefValidationHelper.Factory refDeletionValidatorFactory,
      Provider<InternalChangeQuery> queryProvider) {
    this.identifiedUser = identifiedUser;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.referenceUpdated = referenceUpdated;
    this.refDeletionValidator = refDeletionValidatorFactory.create(DELETE);
    this.queryProvider = queryProvider;
  }

  /**
   * Deletes a single ref from the repository.
   *
   * @param projectState the {@code ProjectState} of the project containing the target ref.
   * @param ref the ref to be deleted.
   * @throws IOException
   * @throws ResourceConflictException
   */
  public void deleteSingleRef(ProjectState projectState, String ref)
      throws IOException, ResourceConflictException, AuthException, PermissionBackendException {
    deleteSingleRef(projectState, ref, null);
  }

  /**
   * Deletes a single ref from the repository.
   *
   * @param projectState the {@code ProjectState} of the project containing the target ref.
   * @param ref the ref to be deleted.
   * @param prefix the prefix of the ref.
   * @throws IOException
   * @throws ResourceConflictException
   */
  public void deleteSingleRef(ProjectState projectState, String ref, @Nullable String prefix)
      throws IOException, ResourceConflictException, AuthException, PermissionBackendException {
    if (prefix != null && !ref.startsWith(R_REFS)) {
      ref = prefix + ref;
    }

    projectState.checkStatePermitsWrite();
    permissionBackend
        .currentUser()
        .project(projectState.getNameKey())
        .ref(ref)
        .check(RefPermission.DELETE);

    try (Repository repository = repoManager.openRepository(projectState.getNameKey())) {
      RefUpdate.Result result;
      RefUpdate u = repository.updateRef(ref);
      u.setExpectedOldObjectId(repository.exactRef(ref).getObjectId());
      u.setNewObjectId(ObjectId.zeroId());
      u.setForceUpdate(true);
      refDeletionValidator.validateRefOperation(projectState.getName(), identifiedUser.get(), u);
      int remainingLockFailureCalls = MAX_LOCK_FAILURE_CALLS;
      for (; ; ) {
        try {
          result = u.delete();
        } catch (LockFailedException e) {
          result = RefUpdate.Result.LOCK_FAILURE;
        }
        if (result == RefUpdate.Result.LOCK_FAILURE && --remainingLockFailureCalls > 0) {
          try {
            Thread.sleep(SLEEP_ON_LOCK_FAILURE_MS);
          } catch (InterruptedException ie) {
            // ignore
          }
        } else {
          break;
        }
      }

      switch (result) {
        case NEW:
        case NO_CHANGE:
        case FAST_FORWARD:
        case FORCED:
          referenceUpdated.fire(
              projectState.getNameKey(),
              u,
              ReceiveCommand.Type.DELETE,
              identifiedUser.get().state());
          break;

        case REJECTED_CURRENT_BRANCH:
          logger.atSevere().log("Cannot delete %s: %s", ref, result.name());
          throw new ResourceConflictException("cannot delete current branch");

        case IO_FAILURE:
        case LOCK_FAILURE:
        case NOT_ATTEMPTED:
        case REJECTED:
        case RENAMED:
        case REJECTED_MISSING_OBJECT:
        case REJECTED_OTHER_REASON:
        default:
          logger.atSevere().log("Cannot delete %s: %s", ref, result.name());
          throw new ResourceConflictException("cannot delete: " + result.name());
      }
    }
  }

  /**
   * Deletes a set of refs from the repository.
   *
   * @param projectState the {@code ProjectState} of the project whose refs are to be deleted.
   * @param refsToDelete the refs to be deleted.
   * @param prefix the prefix of the refs.
   * @throws OrmException
   * @throws IOException
   * @throws ResourceConflictException
   * @throws PermissionBackendException
   */
  public void deleteMultipleRefs(
      ProjectState projectState, ImmutableSet<String> refsToDelete, String prefix)
      throws OrmException, IOException, ResourceConflictException, PermissionBackendException,
          AuthException {
    if (refsToDelete.isEmpty()) {
      return;
    }

    if (refsToDelete.size() == 1) {
      deleteSingleRef(projectState, Iterables.getOnlyElement(refsToDelete), prefix);
      return;
    }

    try (Repository repository = repoManager.openRepository(projectState.getNameKey())) {
      BatchRefUpdate batchUpdate = repository.getRefDatabase().newBatchUpdate();
      batchUpdate.setAtomic(false);
      ImmutableSet<String> refs =
          prefix == null
              ? refsToDelete
              : refsToDelete.stream()
                  .map(ref -> ref.startsWith(R_REFS) ? ref : prefix + ref)
                  .collect(toImmutableSet());
      for (String ref : refs) {
        batchUpdate.addCommand(createDeleteCommand(projectState, repository, ref));
      }
      try (RevWalk rw = new RevWalk(repository)) {
        batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
      }
      StringBuilder errorMessages = new StringBuilder();
      for (ReceiveCommand command : batchUpdate.getCommands()) {
        if (command.getResult() == Result.OK) {
          postDeletion(projectState.getNameKey(), command);
        } else {
          appendAndLogErrorMessage(errorMessages, command);
        }
      }
      if (errorMessages.length() > 0) {
        throw new ResourceConflictException(errorMessages.toString());
      }
    }
  }

  private ReceiveCommand createDeleteCommand(
      ProjectState projectState, Repository r, String refName)
      throws OrmException, IOException, ResourceConflictException, PermissionBackendException {
    Ref ref = r.getRefDatabase().getRef(refName);
    ReceiveCommand command;
    if (ref == null) {
      command = new ReceiveCommand(ObjectId.zeroId(), ObjectId.zeroId(), refName);
      command.setResult(
          Result.REJECTED_OTHER_REASON,
          "it doesn't exist or you do not have permission to delete it");
      return command;
    }
    command = new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), ref.getName());

    if (isConfigRef(refName)) {
      // Never allow to delete the meta config branch.
      command.setResult(Result.REJECTED_OTHER_REASON, "not allowed to delete branch " + refName);
    } else {
      try {
        permissionBackend
            .currentUser()
            .project(projectState.getNameKey())
            .ref(refName)
            .check(RefPermission.DELETE);
      } catch (AuthException denied) {
        command.setResult(
            Result.REJECTED_OTHER_REASON,
            "it doesn't exist or you do not have permission to delete it");
      }
    }

    if (!projectState.statePermitsWrite()) {
      command.setResult(Result.REJECTED_OTHER_REASON, "project state does not permit write");
    }

    if (!refName.startsWith(R_TAGS)) {
      Branch.NameKey branchKey = Branch.nameKey(projectState.getNameKey(), ref.getName());
      if (!queryProvider.get().setLimit(1).byBranchOpen(branchKey).isEmpty()) {
        command.setResult(Result.REJECTED_OTHER_REASON, "it has open changes");
      }
    }

    RefUpdate u = r.updateRef(refName);
    u.setForceUpdate(true);
    u.setExpectedOldObjectId(r.exactRef(refName).getObjectId());
    u.setNewObjectId(ObjectId.zeroId());
    refDeletionValidator.validateRefOperation(projectState.getName(), identifiedUser.get(), u);
    return command;
  }

  private void appendAndLogErrorMessage(StringBuilder errorMessages, ReceiveCommand cmd) {
    String msg;
    switch (cmd.getResult()) {
      case REJECTED_CURRENT_BRANCH:
        msg = format("Cannot delete %s: it is the current branch", cmd.getRefName());
        break;
      case REJECTED_OTHER_REASON:
        msg = format("Cannot delete %s: %s", cmd.getRefName(), cmd.getMessage());
        break;
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case OK:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_NOCREATE:
      case REJECTED_NODELETE:
      case REJECTED_NONFASTFORWARD:
      default:
        msg = format("Cannot delete %s: %s", cmd.getRefName(), cmd.getResult());
        break;
    }
    logger.atSevere().log(msg);
    errorMessages.append(msg);
    errorMessages.append("\n");
  }

  private void postDeletion(Project.NameKey project, ReceiveCommand cmd) {
    referenceUpdated.fire(project, cmd, identifiedUser.get().state());
  }
}
