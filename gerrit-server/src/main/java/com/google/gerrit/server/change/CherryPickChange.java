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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
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
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.CommitsCollection;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
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
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class CherryPickChange {

  private final Provider<ReviewDb> dbProvider;
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
  private final NotifyUtil notifyUtil;
  private final CommitsCollection commitsCollection;
  private final Changes changes;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  CherryPickChange(
      Provider<ReviewDb> dbProvider,
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
      NotifyUtil notifyUtil,
      CommitsCollection commitsCollection,
      Changes changes,
      ChangeNotes.Factory notesFactory) {
    this.dbProvider = dbProvider;
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
    this.notifyUtil = notifyUtil;
    this.commitsCollection = commitsCollection;
    this.changes = changes;
    this.notesFactory = notesFactory;
  }

  public Change.Id cherryPick(
      BatchUpdate.Factory batchUpdateFactory,
      Change change,
      PatchSet patch,
      CherryPickInput input,
      RefControl refControl)
      throws OrmException, IOException, InvalidChangeOperationException, IntegrationException,
          UpdateException, RestApiException {
    return cherryPick(
        batchUpdateFactory,
        change.getId(),
        patch.getId(),
        change.getDest(),
        change.getTopic(),
        change.getProject(),
        ObjectId.fromString(patch.getRevision().get()),
        input,
        refControl);
  }

  public Change.Id cherryPick(
      BatchUpdate.Factory batchUpdateFactory,
      @Nullable Change.Id sourceChangeId,
      @Nullable PatchSet.Id sourcePatchId,
      @Nullable Branch.NameKey sourceBranch,
      @Nullable String sourceChangeTopic,
      Project.NameKey project,
      ObjectId sourceCommit,
      CherryPickInput input,
      RefControl destRefControl)
      throws OrmException, IOException, InvalidChangeOperationException, IntegrationException,
          UpdateException, RestApiException {

    IdentifiedUser identifiedUser = user.get();
    try (Repository git = gitManager.openRepository(project);
        // This inserter and revwalk *must* be passed to any BatchUpdates
        // created later on, to ensure the cherry-picked commit is flushed
        // before patch sets are updated.
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(reader)) {
      String destRefName = destRefControl.getRefName();
      Ref destRef = git.getRefDatabase().exactRef(destRefName);
      if (destRef == null) {
        throw new InvalidChangeOperationException(
            String.format("Branch %s does not exist.", destRefName));
      }

      RevCommit baseCommit =
          getBaseCommit(destRef, destRefControl.getProjectControl(), revWalk, input.base);

      CodeReviewCommit commitToCherryPick = revWalk.parseCommit(sourceCommit);

      if (input.parent <= 0 || input.parent > commitToCherryPick.getParentCount()) {
        throw new InvalidChangeOperationException(
            String.format(
                "Cherry Pick: Parent %s does not exist. Please specify a parent in"
                    + " range [1, %s].",
                input.parent, commitToCherryPick.getParentCount()));
      }

      Timestamp now = TimeUtil.nowTs();
      PersonIdent committerIdent = identifiedUser.newCommitterIdent(now, serverTimeZone);

      final ObjectId computedChangeId =
          ChangeIdUtil.computeChangeId(
              commitToCherryPick.getTree(),
              baseCommit,
              commitToCherryPick.getAuthorIdent(),
              committerIdent,
              input.message);
      String commitMessage = ChangeIdUtil.insertId(input.message, computedChangeId).trim() + '\n';

      CodeReviewCommit cherryPickCommit;
      try {
        ProjectState projectState = destRefControl.getProjectControl().getProjectState();
        cherryPickCommit =
            mergeUtilFactory
                .create(projectState)
                .createCherryPickFromCommit(
                    oi,
                    git.getConfig(),
                    baseCommit,
                    commitToCherryPick,
                    committerIdent,
                    commitMessage,
                    revWalk,
                    input.parent - 1,
                    false);

        Change.Key changeKey;
        final List<String> idList = cherryPickCommit.getFooterLines(FooterConstants.CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          changeKey = new Change.Key(idStr);
        } else {
          changeKey = new Change.Key("I" + computedChangeId.name());
        }

        Branch.NameKey newDest = new Branch.NameKey(project, destRef.getName());
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
            batchUpdateFactory.create(dbProvider.get(), project, identifiedUser, now)) {
          bu.setRepository(git, revWalk, oi);
          Change.Id result;
          if (destChanges.size() == 1) {
            // The change key exists on the destination branch. The cherry pick
            // will be added as a new patch set.
            ChangeControl destCtl =
                destRefControl.getProjectControl().controlFor(destChanges.get(0).notes());
            result = insertPatchSet(bu, git, destCtl, cherryPickCommit, input);
          } else {
            // Change key not found on destination branch. We can create a new
            // change.
            String newTopic = null;
            if (!Strings.isNullOrEmpty(sourceChangeTopic)) {
              newTopic = sourceChangeTopic + "-" + newDest.getShortName();
            }
            result =
                createNewChange(
                    bu,
                    cherryPickCommit,
                    destRefControl.getRefName(),
                    newTopic,
                    sourceBranch,
                    sourceCommit,
                    input);

            if (sourceChangeId != null && sourcePatchId != null) {
              bu.addOp(
                  sourceChangeId,
                  new AddMessageToSourceChangeOp(
                      changeMessagesUtil,
                      sourcePatchId,
                      RefNames.shortName(destRefName),
                      cherryPickCommit));
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

  private RevCommit getBaseCommit(
      Ref destRef, ProjectControl projectControl, RevWalk revWalk, String base)
      throws RestApiException, IOException, OrmException, InvalidChangeOperationException {
    RevCommit destRefTip = revWalk.parseCommit(destRef.getObjectId());
    // The tip commit of the destination ref is the default base for the newly created change.
    if (Strings.isNullOrEmpty(base)) {
      return destRefTip;
    }

    RevCommit baseCommit =
        commitsCollection
            .parse(new ProjectResource(projectControl), IdString.fromDecoded(base))
            .getCommit();

    // The base commit could be a merged commit on the destination ref.
    if (revWalk.isMergedInto(revWalk.parseCommit(baseCommit), destRefTip)) {
      return baseCommit;
    }

    // The base commit could also be a valid change revision.
    List<String> idList = baseCommit.getFooterLines(FooterConstants.CHANGE_ID);
    if (idList.isEmpty()) {
      throw new BadRequestException("change-id is missing in commit " + baseCommit.name());
    }

    String destRefName = RefNames.shortName(destRef.getName());
    String changeId =
        Joiner.on('~')
            .join(
                ImmutableList.of(
                    Url.encode(projectControl.getProject().getName()),
                    Url.encode(destRefName),
                    Url.encode(idList.get(idList.size() - 1).trim())));
    ChangeInfo baseChange = changes.id(changeId).get();
    int changeNum = baseChange._number;

    // The base change status must be NEW.
    if (baseChange.status != ChangeStatus.NEW) {
      throw new BadRequestException(String.format("change %s is not open", changeNum));
    }

    // The base change must contain the corresponding patch set.
    if (!baseChange.revisions.containsKey(baseCommit.name())) {
      throw new ResourceConflictException(
          String.format("commit %s not found in change %s", baseCommit.getName(), changeNum));
    }

    // The corresponding patch set must be visible.
    ReviewDb reviewDb = dbProvider.get();
    ChangeNotes notes =
        notesFactory.createChecked(
            reviewDb, projectControl.getProject().getNameKey(), new Change.Id(changeNum));
    ChangeControl changeControl = projectControl.controlFor(notes);

    int psNum = baseChange.revisions.get(baseCommit.name())._number;
    PatchSet.Id psId = new PatchSet.Id(new Change.Id(changeNum), psNum);
    PatchSet patchSet = psUtil.get(dbProvider.get(), notes, psId);
    if (!changeControl.isPatchVisible(patchSet, reviewDb)) {
      throw new AuthException(
          String.format("patch set %s of change %s is not accessible", psNum, changeNum));
    }

    return baseCommit;
  }

  private Change.Id insertPatchSet(
      BatchUpdate bu,
      Repository git,
      ChangeControl destCtl,
      CodeReviewCommit cherryPickCommit,
      CherryPickInput input)
      throws IOException, OrmException, BadRequestException {
    Change destChange = destCtl.getChange();
    PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
    PatchSet current = psUtil.current(dbProvider.get(), destCtl.getNotes());

    PatchSetInserter inserter = patchSetInserterFactory.create(destCtl, psId, cherryPickCommit);
    inserter
        .setMessage("Uploaded patch set " + inserter.getPatchSetId().get() + ".")
        .setDraft(current.isDraft())
        .setNotify(input.notify)
        .setAccountsToNotify(notifyUtil.resolveAccounts(input.notifyDetails));
    bu.addOp(destChange.getId(), inserter);
    return destChange.getId();
  }

  private Change.Id createNewChange(
      BatchUpdate bu,
      CodeReviewCommit cherryPickCommit,
      String refName,
      String topic,
      Branch.NameKey sourceBranch,
      ObjectId sourceCommit,
      CherryPickInput input)
      throws OrmException, IOException, BadRequestException {
    Change.Id changeId = new Change.Id(seq.nextChangeId());
    ChangeInserter ins =
        changeInserterFactory.create(changeId, cherryPickCommit, refName).setTopic(topic);
    ins.setMessage(messageForDestinationChange(ins.getPatchSetId(), sourceBranch, sourceCommit))
        .setNotify(input.notify)
        .setAccountsToNotify(notifyUtil.resolveAccounts(input.notifyDetails));
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
