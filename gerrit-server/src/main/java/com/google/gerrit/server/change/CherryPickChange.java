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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.TimeZone;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class CherryPickChange {
  private final Provider<ReviewDb> dbProvider;
  private final Sequences seq;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager repoManager;
  private final TimeZone serverTimeZone;
  private final Provider<IdentifiedUser> userProvider;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final PatchSetUtil psUtil;
  private final BatchUpdate.Factory updateFactory;

  @Inject
  CherryPickChange(
      Provider<ReviewDb> dbProvider,
      Sequences seq,
      Provider<InternalChangeQuery> queryProvider,
      @GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager repoManager,
      Provider<IdentifiedUser> userProvider,
      ChangeInserter.Factory changeInserterFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      ChangeMessagesUtil changeMessagesUtil,
      PatchSetUtil psUtil,
      BatchUpdate.Factory batchUpdateFactory) {
    this.dbProvider = dbProvider;
    this.seq = seq;
    this.queryProvider = queryProvider;
    this.repoManager = repoManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.userProvider = userProvider;
    this.changeInserterFactory = changeInserterFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.changeMessagesUtil = changeMessagesUtil;
    this.psUtil = psUtil;
    this.updateFactory = batchUpdateFactory;
  }

  public Change.Id cherryPick(
      Change change,
      PatchSet patch,
      String message,
      ProjectControl projectControl,
      CherryPickDestination destination,
      int parent)
      throws OrmException, IOException, InvalidChangeOperationException, IntegrationException,
          UpdateException, RestApiException {
    return cherryPick(
        change.getId(),
        patch.getId(),
        change.getDest(),
        change.getTopic(),
        ObjectId.fromString(patch.getRevision().get()),
        message,
        projectControl,
        destination,
        parent);
  }

  public Change.Id cherryPick(
      @Nullable Change.Id sourceChangeId,
      @Nullable PatchSet.Id sourcePatchId,
      @Nullable Branch.NameKey sourceBranch,
      @Nullable String sourceChangeTopic,
      ObjectId sourceObjectId,
      String message,
      ProjectControl projectControl,
      CherryPickDestination destination,
      int parent)
      throws OrmException, IOException, InvalidChangeOperationException, IntegrationException,
          UpdateException, RestApiException {
    Project.NameKey project = projectControl.getProject().getNameKey();
    try (Repository repo = repoManager.openRepository(project);
        // This inserter and revwalk *must* be passed to any BatchUpdates created later on, to
        // ensure the cherry-picked commit is flushed before patch sets are updated.
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(reader)) {
      CodeReviewCommit sourceCommit = revWalk.parseCommit(sourceObjectId);
      if (parent <= 0 || parent > sourceCommit.getParentCount()) {
        throw new InvalidChangeOperationException(
            String.format(
                "Cherry Pick: Parent %s does not exist. Please specify a parent in"
                    + " range [1, %s].",
                parent, sourceCommit.getParentCount()));
      }

      Timestamp now = TimeUtil.nowTs();
      PersonIdent committerIdent = userProvider.get().newCommitterIdent(now, serverTimeZone);

      CodeReviewCommit mergeTip = revWalk.parseCommit(destination.mergeTip());
      ObjectId computedChangeId =
          ChangeIdUtil.computeChangeId(
              sourceCommit.getTree(),
              mergeTip,
              sourceCommit.getAuthorIdent(),
              committerIdent,
              message);
      String commitMessage = ChangeIdUtil.insertId(message, computedChangeId).trim() + '\n';
      CodeReviewCommit commit;
      try {
        commit =
            mergeUtilFactory
                .create(projectControl.getProjectState())
                .createCherryPickFromCommit(
                    oi,
                    repo.getConfig(),
                    mergeTip,
                    sourceCommit,
                    committerIdent,
                    commitMessage,
                    revWalk,
                    parent - 1,
                    false);

        Change.Key changeKey;
        final List<String> idList = commit.getFooterLines(FooterConstants.CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          changeKey = new Change.Key(idStr);
        } else {
          changeKey = new Change.Key("I" + computedChangeId.name());
        }

        String targetRef = destination.targetRef();
        Branch.NameKey newDest = new Branch.NameKey(project, targetRef);
        List<ChangeData> destChanges =
            queryProvider.get().setLimit(2).byBranchKey(newDest, changeKey);

        if (destination.targetAtChange()) {
          if (destChanges.size() > 0) {
            throw new ResourceConflictException(
                "target branch contains a change with key " + changeKey);
          }
        } else if (destChanges.size() > 1) {
          throw new InvalidChangeOperationException(
              "target branch contains several changes with key "
                  + changeKey
                  + ". Cannot create a new patch set.");
        }

        try (BatchUpdate bu =
            updateFactory.create(dbProvider.get(), project, userProvider.get(), now)) {
          bu.setRepository(repo, revWalk, oi);
          Change.Id result;
          if (destChanges.size() == 1) {
            // The change key exists on the destination branch. The cherry pick will be added as a
            // new patch set.
            ChangeControl destCtl = projectControl.controlFor(destChanges.get(0).notes());
            result = insertPatchSet(bu, repo, destCtl, commit);
          } else {
            // Change key not found on destination branch. We can create a new change.
            String newTopic = null;
            if (!Strings.isNullOrEmpty(sourceChangeTopic)) {
              newTopic = sourceChangeTopic + "-" + newDest.getShortName();
            }
            result = createNewChange(bu, commit, targetRef, newTopic, sourceBranch, sourceObjectId);

            if (sourceChangeId != null && sourcePatchId != null) {
              bu.addOp(
                  sourceChangeId,
                  new AddMessageToSourceChangeOp(
                      changeMessagesUtil, sourcePatchId, newDest.getShortName(), commit));
            }
          }
          bu.execute();
          return result;
        }
      } catch (MergeIdenticalTreeException | MergeConflictException e) {
        throw new IntegrationException("Cherry pick failed: " + e.getMessage());
      }
    }
  }

  private Change.Id insertPatchSet(
      BatchUpdate bu, Repository git, ChangeControl destCtl, CodeReviewCommit cherryPickCommit)
      throws IOException, OrmException {
    Change destChange = destCtl.getChange();
    PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
    PatchSetInserter inserter = patchSetInserterFactory.create(destCtl, psId, cherryPickCommit);
    PatchSet.Id newPatchSetId = inserter.getPatchSetId();
    PatchSet current = psUtil.current(dbProvider.get(), destCtl.getNotes());

    bu.addOp(
        destChange.getId(),
        inserter
            .setMessage("Uploaded patch set " + newPatchSetId.get() + ".")
            .setDraft(current.isDraft())
            .setNotify(NotifyHandling.NONE));
    return destChange.getId();
  }

  private Change.Id createNewChange(
      BatchUpdate bu,
      CodeReviewCommit cherryPickCommit,
      String refName,
      String topic,
      Branch.NameKey sourceBranch,
      ObjectId sourceCommit)
      throws OrmException, IOException {
    Change.Id changeId = new Change.Id(seq.nextChangeId());
    ChangeInserter ins =
        changeInserterFactory.create(changeId, cherryPickCommit, refName).setTopic(topic);
    ins.setMessage(messageForDestinationChange(ins.getPatchSetId(), sourceBranch, sourceCommit));
    bu.insertChange(ins);
    return changeId;
  }

  private static class AddMessageToSourceChangeOp implements BatchUpdateOp {
    private final ChangeMessagesUtil cmUtil;
    private final PatchSet.Id psId;
    private final String destBranch;
    private final ObjectId cherryPickCommit;

    private AddMessageToSourceChangeOp(
        ChangeMessagesUtil cmUtil, PatchSet.Id psId, String destBranch, ObjectId cherryPickCommit) {
      this.cmUtil = cmUtil;
      this.psId = psId;
      this.destBranch = destBranch;
      this.cherryPickCommit = cherryPickCommit;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws OrmException {
      StringBuilder sb =
          new StringBuilder("Patch Set ")
              .append(psId.get())
              .append(": Cherry Picked")
              .append("\n\n")
              .append("This patchset was cherry picked to branch ")
              .append(destBranch)
              .append(" as commit ")
              .append(cherryPickCommit.name());
      ChangeMessage changeMessage =
          ChangeMessagesUtil.newMessage(
              psId,
              ctx.getUser(),
              ctx.getWhen(),
              sb.toString(),
              ChangeMessagesUtil.TAG_CHERRY_PICK_CHANGE);
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getUpdate(psId), changeMessage);
      return true;
    }
  }

  private String messageForDestinationChange(
      PatchSet.Id patchSetId, Branch.NameKey sourceBranch, ObjectId sourceCommit) {
    StringBuilder stringBuilder = new StringBuilder("Patch Set ").append(patchSetId.get());

    if (sourceBranch != null) {
      stringBuilder.append(": Cherry Picked from branch ").append(sourceBranch.getShortName());
    } else {
      stringBuilder.append(": Cherry Picked from commit ").append(sourceCommit.getName());
    }

    return stringBuilder.append(".").toString();
  }
}
