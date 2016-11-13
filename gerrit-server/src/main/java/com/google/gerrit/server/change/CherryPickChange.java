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
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.TimeZone;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class CherryPickChange {

  private final Provider<ReviewDb> db;
  private final Sequences seq;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager gitManager;
  private final TimeZone serverTimeZone;
  private final Provider<IdentifiedUser> user;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final PatchSetUtil psUtil;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  CherryPickChange(
      Provider<ReviewDb> db,
      Sequences seq,
      Provider<InternalChangeQuery> queryProvider,
      @GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager gitManager,
      Provider<IdentifiedUser> user,
      ChangeInserter.Factory changeInserterFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      ChangeMessagesUtil changeMessagesUtil,
      PatchSetUtil psUtil,
      BatchUpdate.Factory batchUpdateFactory) {
    this.db = db;
    this.seq = seq;
    this.queryProvider = queryProvider;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.user = user;
    this.changeInserterFactory = changeInserterFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.changeMessagesUtil = changeMessagesUtil;
    this.psUtil = psUtil;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  public Change.Id cherryPick(
      Change change,
      PatchSet patch,
      final String message,
      final String ref,
      final RefControl refControl,
      int parent)
      throws NoSuchChangeException, OrmException, MissingObjectException,
          IncorrectObjectTypeException, IOException, InvalidChangeOperationException,
          IntegrationException, UpdateException, RestApiException {

    if (Strings.isNullOrEmpty(ref)) {
      throw new InvalidChangeOperationException(
          "Cherry Pick: Destination branch cannot be null or empty");
    }

    Project.NameKey project = change.getProject();
    String destinationBranch = RefNames.shortName(ref);
    IdentifiedUser identifiedUser = user.get();
    try (Repository git = gitManager.openRepository(project);
        // This inserter and revwalk *must* be passed to any BatchUpdates
        // created later on, to ensure the cherry-picked commit is flushed
        // before patch sets are updated.
        ObjectInserter oi = git.newObjectInserter();
        CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(oi.newReader())) {
      Ref destRef = git.getRefDatabase().exactRef(ref);
      if (destRef == null) {
        throw new InvalidChangeOperationException(
            String.format("Branch %s does not exist.", destinationBranch));
      }

      CodeReviewCommit mergeTip = revWalk.parseCommit(destRef.getObjectId());

      CodeReviewCommit commitToCherryPick =
          revWalk.parseCommit(ObjectId.fromString(patch.getRevision().get()));

      if (parent <= 0 || parent > commitToCherryPick.getParentCount()) {
        throw new InvalidChangeOperationException(
            String.format(
                "Cherry Pick: Parent %s does not exist. Please specify a parent in"
                    + " range [1, %s].",
                parent, commitToCherryPick.getParentCount()));
      }

      Timestamp now = TimeUtil.nowTs();
      PersonIdent committerIdent = identifiedUser.newCommitterIdent(now, serverTimeZone);

      final ObjectId computedChangeId =
          ChangeIdUtil.computeChangeId(
              commitToCherryPick.getTree(),
              mergeTip,
              commitToCherryPick.getAuthorIdent(),
              committerIdent,
              message);
      String commitMessage = ChangeIdUtil.insertId(message, computedChangeId).trim() + '\n';

      CodeReviewCommit cherryPickCommit;
      try {
        ProjectState projectState = refControl.getProjectControl().getProjectState();
        cherryPickCommit =
            mergeUtilFactory
                .create(projectState)
                .createCherryPickFromCommit(
                    git,
                    oi,
                    mergeTip,
                    commitToCherryPick,
                    committerIdent,
                    commitMessage,
                    revWalk,
                    parent - 1);

        Change.Key changeKey;
        final List<String> idList = cherryPickCommit.getFooterLines(FooterConstants.CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          changeKey = new Change.Key(idStr);
        } else {
          changeKey = new Change.Key("I" + computedChangeId.name());
        }

        Branch.NameKey newDest = new Branch.NameKey(change.getProject(), destRef.getName());
        List<ChangeData> destChanges =
            queryProvider.get().setLimit(2).byBranchKey(newDest, changeKey);
        if (destChanges.size() > 1) {
          throw new InvalidChangeOperationException(
              "Several changes with key "
                  + changeKey
                  + " reside on the same branch. "
                  + "Cannot create a new patch set.");
        }
        try (BatchUpdate bu =
            batchUpdateFactory.create(
                db.get(), change.getDest().getParentKey(), identifiedUser, now)) {
          bu.setRepository(git, revWalk, oi);
          Change.Id result;
          if (destChanges.size() == 1) {
            // The change key exists on the destination branch. The cherry pick
            // will be added as a new patch set.
            ChangeControl destCtl =
                refControl.getProjectControl().controlFor(destChanges.get(0).notes());
            result = insertPatchSet(bu, git, destCtl, cherryPickCommit);
          } else {
            // Change key not found on destination branch. We can create a new
            // change.
            String newTopic = null;
            if (!Strings.isNullOrEmpty(change.getTopic())) {
              newTopic = change.getTopic() + "-" + newDest.getShortName();
            }
            result =
                createNewChange(
                    bu, cherryPickCommit, refControl.getRefName(), newTopic, change.getDest());

            bu.addOp(
                change.getId(),
                new AddMessageToSourceChangeOp(
                    changeMessagesUtil, patch.getId(), destinationBranch, cherryPickCommit));
          }
          bu.execute();
          return result;
        }
      } catch (MergeIdenticalTreeException | MergeConflictException e) {
        throw new IntegrationException("Cherry pick failed: " + e.getMessage());
      }
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(change.getId(), e);
    }
  }

  private Change.Id insertPatchSet(
      BatchUpdate bu, Repository git, ChangeControl destCtl, CodeReviewCommit cherryPickCommit)
      throws IOException, OrmException {
    Change destChange = destCtl.getChange();
    PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
    PatchSetInserter inserter = patchSetInserterFactory.create(destCtl, psId, cherryPickCommit);
    PatchSet.Id newPatchSetId = inserter.getPatchSetId();
    PatchSet current = psUtil.current(db.get(), destCtl.getNotes());

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
      Branch.NameKey sourceBranch)
      throws OrmException {
    Change.Id changeId = new Change.Id(seq.nextChangeId());
    ChangeInserter ins =
        changeInserterFactory
            .create(changeId, cherryPickCommit, refName)
            .setValidatePolicy(CommitValidators.Policy.GERRIT)
            .setTopic(topic);

    ins.setMessage(messageForDestinationChange(ins.getPatchSetId(), sourceBranch));
    bu.insertChange(ins);
    return changeId;
  }

  private static class AddMessageToSourceChangeOp extends BatchUpdate.Op {
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
              ctx.getDb(),
              psId,
              ctx.getUser(),
              ctx.getWhen(),
              sb.toString(),
              ChangeMessagesUtil.TAG_CHERRY_PICK_CHANGE);
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getUpdate(psId), changeMessage);
      return true;
    }
  }

  private String messageForDestinationChange(PatchSet.Id patchSetId, Branch.NameKey sourceBranch) {
    return new StringBuilder("Patch Set ")
        .append(patchSetId.get())
        .append(": Cherry Picked from branch ")
        .append(sourceBranch.getShortName())
        .append(".")
        .toString();
  }
}
