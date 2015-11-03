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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.query.change.ChangeData.asChanges;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.MoveInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Collections;

@Singleton
public class Move implements RestModifyView<ChangeResource, MoveInput> {
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final GitRepositoryManager repoManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  Move(Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      GitRepositoryManager repoManager,
      Provider<InternalChangeQuery> queryProvider,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory) {
    this.dbProvider = dbProvider;
    this.json = json;
    this.repoManager = repoManager;
    this.queryProvider = queryProvider;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  @Override
  public ChangeInfo apply(ChangeResource req, MoveInput input)
      throws RestApiException, OrmException, UpdateException {
    ChangeControl control = req.getControl();
    input.destination = RefNames.fullName(input.destination);
    if (!control.canMoveTo(input.destination, dbProvider.get())) {
      throw new AuthException("Move not permitted");
    }

    try (BatchUpdate u = batchUpdateFactory.create(dbProvider.get(),
        req.getChange().getProject(), control.getUser(), TimeUtil.nowTs())) {
      u.addOp(req.getChange().getId(), new Op(control, input));
      u.execute();
    }

    return json.create(ChangeJson.NO_OPTIONS).format(req.getChange());
  }

  private class Op extends BatchUpdate.Op {
    private final MoveInput input;
    private final IdentifiedUser caller;

    private Change change;
    private Branch.NameKey newDestKey;

    public Op(ChangeControl ctl, MoveInput input) {
      this.input = input;
      this.caller = ctl.getUser().asIdentifiedUser();
    }

    @Override
    public void updateChange(ChangeContext ctx) throws OrmException,
        ResourceConflictException, RepositoryNotFoundException, IOException {
      change = ctx.getChange();
      if (change.getStatus() != Status.NEW
          && change.getStatus() != Status.DRAFT) {
        throw new ResourceConflictException("Change is " + status(change));
      }

      Project.NameKey projectKey = change.getProject();
      newDestKey = new Branch.NameKey(projectKey, input.destination);
      Branch.NameKey changePrevDest = change.getDest();
      if (changePrevDest.equals(newDestKey)) {
        throw new ResourceConflictException(
            "Change is already destined for the specified branch");
      }

      final PatchSet.Id patchSetId = change.currentPatchSetId();
      try (Repository repo = repoManager.openRepository(projectKey);
          RevWalk revWalk = new RevWalk(repo)) {
        String currPatchsetRev =
            dbProvider.get().patchSets().get(patchSetId).getRevision().get();
        RevCommit currPatchsetRevCommit =
            revWalk.parseCommit(ObjectId.fromString((currPatchsetRev)));
        if (currPatchsetRevCommit.getParentCount() > 1) {
          throw new ResourceConflictException("Merge commit cannot be moved");
        }

        ObjectId refId = repo.resolve(input.destination);
        // Check if destination ref exists in project repo
        if (refId == null) {
          throw new ResourceConflictException(
              "Destination " + input.destination + " not found in the project");
        }
        RevCommit refCommit = revWalk.parseCommit(refId);
        if (revWalk.isMergedInto(currPatchsetRevCommit, refCommit)) {
          throw new ResourceConflictException(
              "Current patchset revision is reachable from tip of "
                  + input.destination);
        }
      }

      Change.Key changeKey = change.getKey();
      if (!asChanges(queryProvider.get().byBranchKey(newDestKey, changeKey))
          .isEmpty()) {
        throw new ResourceConflictException(
            "Destination " + newDestKey.getShortName()
                + " has a different change with same change key " + changeKey);
      }

      if (!change.currentPatchSetId().equals(patchSetId)) {
        throw new ResourceConflictException("Patch set is not current");
      }

      change.setDest(newDestKey);
      ChangeUtil.updated(change);
      ctx.getDb().changes().update(Collections.singleton(change));

      StringBuilder msgBuf = new StringBuilder();
      msgBuf.append("Patch set ");
      msgBuf.append(patchSetId.get());
      msgBuf.append(": Change destination moved from ");
      msgBuf.append(changePrevDest.getShortName());
      msgBuf.append(" to ");
      msgBuf.append(newDestKey.getShortName());
      if (!Strings.isNullOrEmpty(input.message)) {
        msgBuf.append("\n\n");
        msgBuf.append(input.message);
      }
      ChangeMessage cmsg = new ChangeMessage(
          new ChangeMessage.Key(change.getId(),
              ChangeUtil.messageUUID(ctx.getDb())),
          caller.getAccountId(), ctx.getWhen(), change.currentPatchSetId());
      cmsg.setMessage(msgBuf.toString());
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getChangeUpdate(), cmsg);
    }
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
