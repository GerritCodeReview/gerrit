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

import static java.lang.String.format;

import com.google.common.collect.Lists;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.DeleteBranches.Input;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Singleton
class DeleteBranches implements RestModifyView<ProjectResource, Input> {
  private static final Logger log = LoggerFactory.getLogger(DeleteBranches.class);

  static class Input {
    List<String> branches;

    static Input init(Input in) {
      if (in == null) {
        in = new Input();
      }
      if (in.branches == null) {
        in.branches = Lists.newArrayListWithCapacity(1);
      }
      return in;
    }
  }

  private final Provider<IdentifiedUser> identifiedUser;
  private final GitRepositoryManager repoManager;
  private final Provider<ReviewDb> dbProvider;
  private final GitReferenceUpdated referenceUpdated;
  private final ChangeHooks hooks;

  @Inject
  DeleteBranches(Provider<IdentifiedUser> identifiedUser,
      GitRepositoryManager repoManager, Provider<ReviewDb> dbProvider,
      GitReferenceUpdated referenceUpdated, ChangeHooks hooks) {
    this.identifiedUser = identifiedUser;
    this.repoManager = repoManager;
    this.dbProvider = dbProvider;
    this.referenceUpdated = referenceUpdated;
    this.hooks = hooks;
  }

  @Override
  public Response<?> apply(ProjectResource project, Input input)
      throws OrmException, IOException, ResourceConflictException {

    input = Input.init(input);
    Repository r = repoManager.openRepository(project.getNameKey());
    try {
      BatchRefUpdate batchUpdate = r.getRefDatabase().newBatchUpdate();
      for (String branch : input.branches) {
        Ref ref = r.getRefDatabase().getRef(branch);
        if (ref == null){
          continue;
        }
        batchUpdate.addCommand(createDeleteCommand(project, ref));
      }
      batchUpdate.execute(new RevWalk(r), NullProgressMonitor.INSTANCE);
      StringBuilder errorMessages = new StringBuilder();
      for (ReceiveCommand command : batchUpdate.getCommands()) {
        if(command.getResult() == Result.OK){
          referenceUpdated.fire(project.getNameKey(), command.getRefName(),
              command.getOldId(), command.getNewId());
          Branch.NameKey branchKey =
              new Branch.NameKey(project.getNameKey(), command.getRefName());
          hooks.doRefUpdatedHook(branchKey, command.getOldId(),
              command.getNewId(), identifiedUser.get().getAccount());
          ResultSet<SubmoduleSubscription> submoduleSubscriptions =
              dbProvider.get().submoduleSubscriptions()
                  .bySuperProject(branchKey);
          dbProvider.get().submoduleSubscriptions()
              .delete(submoduleSubscriptions);
        }else{
          appendAndLogErrorMessage(errorMessages, command);
        }
      }
      if(errorMessages.length()>0){
        throw new ResourceConflictException(errorMessages.toString());
      }
    } finally {
      r.close();
    }
    return Response.none();
  }

  private ReceiveCommand createDeleteCommand(ProjectResource project, Ref ref)
      throws OrmException {
    ReceiveCommand command =
        new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), ref.getName());
    Branch.NameKey branchKey =
        new Branch.NameKey(project.getNameKey(), ref.getName());
    if (!project.getControl().controlForRef(branchKey).canDelete()) {
      command.setResult(Result.REJECTED_OTHER_REASON,
          "user does not have rights to delete it.");
    }
    if (dbProvider.get().changes().byBranchOpenAll(branchKey).iterator()
        .hasNext()) {
      command.setResult(Result.REJECTED_OTHER_REASON, "it has open changes");
    }
    return command;
  }

  private void appendAndLogErrorMessage(StringBuilder errorMessages,
      ReceiveCommand cmd) {
    String msg = null;
    switch (cmd.getResult()) {
      case REJECTED_CURRENT_BRANCH:
        msg = format("Cannot delete %s: it is the current branch",
            cmd.getRefName());
        log.warn(msg);
        break;
      case REJECTED_OTHER_REASON:
        msg = format("Cannot delete %s: %s", cmd.getRefName(), cmd.getMessage());
        log.error(msg);
        break;
      default:
        msg = format("Cannot delete %s: %s", cmd.getRefName(), cmd.getResult());
        log.error(msg);
    }
    errorMessages.append(msg);
    errorMessages.append("\n");
  }
}
