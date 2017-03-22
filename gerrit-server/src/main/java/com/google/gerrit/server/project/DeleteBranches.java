// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class DeleteBranches implements RestModifyView<ProjectResource, DeleteBranchesInput> {
  private final DeleteRef.Factory deleteRefFactory;

  @Inject
  DeleteBranches(DeleteRef.Factory deleteRefFactory) {
    this.deleteRefFactory = deleteRefFactory;
  }

  @Override
  public Response<?> apply(ProjectResource project, DeleteBranchesInput input)
      throws OrmException, IOException, RestApiException {

    if (input == null || input.branches == null || input.branches.isEmpty()) {
      throw new BadRequestException("branches must be specified");
    }

    deleteRefFactory.create(project).refs(input.branches).delete();
    return Response.none();
  }

  private ReceiveCommand createDeleteCommand(ProjectResource project,
      Repository r, String branch)
          throws OrmException, IOException, ResourceConflictException {
    Ref ref = r.getRefDatabase().getRef(branch);
    ReceiveCommand command;
    if (ref == null) {
      command = new ReceiveCommand(ObjectId.zeroId(), ObjectId.zeroId(), branch);
      command.setResult(Result.REJECTED_OTHER_REASON,
          "it doesn't exist or you do not have permission to delete it");
      return command;
    }
    command =
        new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), ref.getName());
    Branch.NameKey branchKey =
        new Branch.NameKey(project.getNameKey(), ref.getName());
    if (!project.getControl().controlForRef(branchKey).canDelete()) {
      command.setResult(Result.REJECTED_OTHER_REASON,
          "it doesn't exist or you do not have permission to delete it");
    }
    if (!queryProvider.get().setLimit(1).byBranchOpen(branchKey).isEmpty()) {
      command.setResult(Result.REJECTED_OTHER_REASON, "it has open changes");
    }
    RefUpdate u = r.updateRef(branch);
    u.setExpectedOldObjectId(r.exactRef(branch).getObjectId());
    u.setNewObjectId(ObjectId.zeroId());
    u.setForceUpdate(true);
    refDeletionValidator.validateRefOperation(
        project.getName(), identifiedUser.get(), u);
    return command;
  }

  private void appendAndLogErrorMessage(StringBuilder errorMessages,
      ReceiveCommand cmd) {
    String msg = null;
    switch (cmd.getResult()) {
      case REJECTED_CURRENT_BRANCH:
        msg = format("Cannot delete %s: it is the current branch",
            cmd.getRefName());
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
    referenceUpdated.fire(project.getNameKey(), cmd,
        identifiedUser.get().getAccount());
  }
}
