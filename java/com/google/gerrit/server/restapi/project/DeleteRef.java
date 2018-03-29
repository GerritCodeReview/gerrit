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

import static com.google.gerrit.reviewdb.client.RefNames.isConfigRef;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.RefValidationHelper;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteRef {
  private static final Logger log = LoggerFactory.getLogger(DeleteRef.class);

  private static final int MAX_LOCK_FAILURE_CALLS = 10;
  private static final long SLEEP_ON_LOCK_FAILURE_MS = 15;

  private final Provider<IdentifiedUser> identifiedUser;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated referenceUpdated;
  private final RefValidationHelper refDeletionValidator;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ProjectResource resource;
  private final List<String> refsToDelete;
  private String prefix;

  public interface Factory {
    DeleteRef create(ProjectResource r);
  }

  @Inject
  DeleteRef(
      Provider<IdentifiedUser> identifiedUser,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      GitReferenceUpdated referenceUpdated,
      RefValidationHelper.Factory refDeletionValidatorFactory,
      Provider<InternalChangeQuery> queryProvider,
      @Assisted ProjectResource resource) {
    this.identifiedUser = identifiedUser;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.referenceUpdated = referenceUpdated;
    this.refDeletionValidator = refDeletionValidatorFactory.create(DELETE);
    this.queryProvider = queryProvider;
    this.resource = resource;
    this.refsToDelete = new ArrayList<>();
  }

  public DeleteRef ref(String ref) {
    this.refsToDelete.add(ref);
    return this;
  }

  public DeleteRef refs(List<String> refs) {
    this.refsToDelete.addAll(refs);
    return this;
  }

  public DeleteRef prefix(String prefix) {
    this.prefix = prefix;
    return this;
  }

  public void delete()
      throws OrmException, IOException, ResourceConflictException, PermissionBackendException {
    if (!refsToDelete.isEmpty()) {
      try (Repository r = repoManager.openRepository(resource.getNameKey())) {
        if (refsToDelete.size() == 1) {
          deleteSingleRef(r);
        } else {
          deleteMultipleRefs(r);
        }
      }
    }
  }

  private void deleteSingleRef(Repository r) throws IOException, ResourceConflictException {
    String ref = refsToDelete.get(0);
    if (prefix != null && !ref.startsWith(R_REFS)) {
      ref = prefix + ref;
    }
    RefUpdate.Result result;
    RefUpdate u = r.updateRef(ref);
    u.setExpectedOldObjectId(r.exactRef(ref).getObjectId());
    u.setNewObjectId(ObjectId.zeroId());
    u.setForceUpdate(true);
    refDeletionValidator.validateRefOperation(resource.getName(), identifiedUser.get(), u);
    int remainingLockFailureCalls = MAX_LOCK_FAILURE_CALLS;
    for (; ; ) {
      try {
        result = u.delete();
      } catch (LockFailedException e) {
        result = RefUpdate.Result.LOCK_FAILURE;
      } catch (IOException e) {
        log.error("Cannot delete " + ref, e);
        throw e;
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
            resource.getNameKey(), u, ReceiveCommand.Type.DELETE, identifiedUser.get().state());
        break;

      case REJECTED_CURRENT_BRANCH:
        log.error("Cannot delete " + ref + ": " + result.name());
        throw new ResourceConflictException("cannot delete current branch");

      case IO_FAILURE:
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case RENAMED:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      default:
        log.error("Cannot delete " + ref + ": " + result.name());
        throw new ResourceConflictException("cannot delete: " + result.name());
    }
  }

  private void deleteMultipleRefs(Repository r)
      throws OrmException, IOException, ResourceConflictException, PermissionBackendException {
    BatchRefUpdate batchUpdate = r.getRefDatabase().newBatchUpdate();
    batchUpdate.setAtomic(false);
    List<String> refs =
        prefix == null
            ? refsToDelete
            : refsToDelete
                .stream()
                .map(ref -> ref.startsWith(R_REFS) ? ref : prefix + ref)
                .collect(toList());
    for (String ref : refs) {
      batchUpdate.addCommand(createDeleteCommand(resource, r, ref));
    }
    try (RevWalk rw = new RevWalk(r)) {
      batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
    }
    StringBuilder errorMessages = new StringBuilder();
    for (ReceiveCommand command : batchUpdate.getCommands()) {
      if (command.getResult() == Result.OK) {
        postDeletion(resource, command);
      } else {
        appendAndLogErrorMessage(errorMessages, command);
      }
    }
    if (errorMessages.length() > 0) {
      throw new ResourceConflictException(errorMessages.toString());
    }
  }

  private ReceiveCommand createDeleteCommand(ProjectResource project, Repository r, String refName)
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
            .project(project.getNameKey())
            .ref(refName)
            .check(RefPermission.DELETE);
      } catch (AuthException denied) {
        command.setResult(
            Result.REJECTED_OTHER_REASON,
            "it doesn't exist or you do not have permission to delete it");
      }
    }

    if (!project.getProjectState().statePermitsWrite()) {
      command.setResult(Result.REJECTED_OTHER_REASON, "project state does not permit write");
    }

    if (!refName.startsWith(R_TAGS)) {
      Branch.NameKey branchKey = new Branch.NameKey(project.getNameKey(), ref.getName());
      if (!queryProvider.get().setLimit(1).byBranchOpen(branchKey).isEmpty()) {
        command.setResult(Result.REJECTED_OTHER_REASON, "it has open changes");
      }
    }

    RefUpdate u = r.updateRef(refName);
    u.setForceUpdate(true);
    u.setExpectedOldObjectId(r.exactRef(refName).getObjectId());
    u.setNewObjectId(ObjectId.zeroId());
    refDeletionValidator.validateRefOperation(project.getName(), identifiedUser.get(), u);
    return command;
  }

  private void appendAndLogErrorMessage(StringBuilder errorMessages, ReceiveCommand cmd) {
    String msg = null;
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
    log.error(msg);
    errorMessages.append(msg);
    errorMessages.append("\n");
  }

  private void postDeletion(ProjectResource project, ReceiveCommand cmd) {
    referenceUpdated.fire(project.getNameKey(), cmd, identifiedUser.get().state());
  }
}
