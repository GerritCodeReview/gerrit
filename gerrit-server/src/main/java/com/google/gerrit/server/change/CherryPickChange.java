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
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.notedb.ChangeUpdate;
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

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

import java.io.IOException;
import java.util.List;
import java.util.TimeZone;

@Singleton
public class CherryPickChange {

  private final Provider<ReviewDb> db;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager gitManager;
  private final TimeZone serverTimeZone;
  private final Provider<IdentifiedUser> user;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final ChangeUpdate.Factory updateFactory;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  CherryPickChange(Provider<ReviewDb> db,
      Provider<InternalChangeQuery> queryProvider,
      @GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager gitManager,
      Provider<IdentifiedUser> user,
      ChangeInserter.Factory changeInserterFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      ChangeMessagesUtil changeMessagesUtil,
      ChangeUpdate.Factory updateFactory,
      BatchUpdate.Factory batchUpdateFactory) {
    this.db = db;
    this.queryProvider = queryProvider;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.user = user;
    this.changeInserterFactory = changeInserterFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.changeMessagesUtil = changeMessagesUtil;
    this.updateFactory = updateFactory;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  public Change.Id cherryPick(Change change, PatchSet patch,
      final String message, final String ref,
      final RefControl refControl) throws NoSuchChangeException,
      OrmException, MissingObjectException,
      IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException, IntegrationException, UpdateException,
      RestApiException {

    if (Strings.isNullOrEmpty(ref)) {
      throw new InvalidChangeOperationException(
          "Cherry Pick: Destination branch cannot be null or empty");
    }

    Project.NameKey project = change.getProject();
    String destinationBranch = RefNames.shortName(ref);
    IdentifiedUser identifiedUser = user.get();
    try (Repository git = gitManager.openRepository(project);
        CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(git)) {
      Ref destRef = git.getRefDatabase().exactRef(ref);
      if (destRef == null) {
        throw new InvalidChangeOperationException(String.format(
            "Branch %s does not exist.", destinationBranch));
      }

      CodeReviewCommit mergeTip = revWalk.parseCommit(destRef.getObjectId());

      CodeReviewCommit commitToCherryPick =
          revWalk.parseCommit(ObjectId.fromString(patch.getRevision().get()));

      PersonIdent committerIdent =
          identifiedUser.newCommitterIdent(TimeUtil.nowTs(),
              serverTimeZone);

      final ObjectId computedChangeId =
          ChangeIdUtil
              .computeChangeId(commitToCherryPick.getTree(), mergeTip,
                  commitToCherryPick.getAuthorIdent(), committerIdent, message);
      String commitMessage =
          ChangeIdUtil.insertId(message, computedChangeId).trim() + '\n';

      CodeReviewCommit cherryPickCommit;
      try (ObjectInserter oi = git.newObjectInserter()) {
        ProjectState projectState = refControl.getProjectControl().getProjectState();
        cherryPickCommit =
            mergeUtilFactory.create(projectState).createCherryPickFromCommit(git, oi, mergeTip,
                commitToCherryPick, committerIdent, commitMessage, revWalk);

        Change.Key changeKey;
        final List<String> idList = cherryPickCommit.getFooterLines(
            FooterConstants.CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          changeKey = new Change.Key(idStr);
        } else {
          changeKey = new Change.Key("I" + computedChangeId.name());
        }

        Branch.NameKey newDest =
            new Branch.NameKey(change.getProject(), destRef.getName());
        List<ChangeData> destChanges = queryProvider.get()
            .setLimit(2)
            .byBranchKey(newDest, changeKey);
        if (destChanges.size() > 1) {
          throw new InvalidChangeOperationException("Several changes with key "
              + changeKey + " reside on the same branch. "
              + "Cannot create a new patch set.");
        } else if (destChanges.size() == 1) {
          // The change key exists on the destination branch. The cherry pick
          // will be added as a new patch set.
          return insertPatchSet(git, revWalk, oi, destChanges.get(0).change(),
              cherryPickCommit, refControl, identifiedUser);
        } else {
          // Change key not found on destination branch. We can create a new
          // change.
          String newTopic = null;
          if (!Strings.isNullOrEmpty(change.getTopic())) {
            newTopic = change.getTopic() + "-" + newDest.getShortName();
          }
          Change newChange = createNewChange(git, revWalk, oi, changeKey,
              project, destRef, cherryPickCommit, refControl, identifiedUser,
              newTopic, change.getDest());

          addMessageToSourceChange(change, patch.getId(), destinationBranch,
              cherryPickCommit, identifiedUser, refControl);

          return newChange.getId();
        }
      } catch (MergeIdenticalTreeException | MergeConflictException e) {
        throw new IntegrationException("Cherry pick failed: " + e.getMessage());
      }
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(change.getId(), e);
    }
  }

  private Change.Id insertPatchSet(Repository git, RevWalk revWalk,
      ObjectInserter oi, Change change, CodeReviewCommit cherryPickCommit,
      RefControl refControl, IdentifiedUser identifiedUser)
      throws IOException, OrmException, UpdateException, RestApiException {
    PatchSet.Id psId =
        ChangeUtil.nextPatchSetId(git, change.currentPatchSetId());
    PatchSetInserter inserter = patchSetInserterFactory
        .create(refControl, psId, cherryPickCommit);
    PatchSet.Id newPatchSetId = inserter.getPatchSetId();
    PatchSet current = db.get().patchSets().get(change.currentPatchSetId());

    try (BatchUpdate bu = batchUpdateFactory.create(
        db.get(), change.getDest().getParentKey(), identifiedUser,
        TimeUtil.nowTs())) {
      bu.setRepository(git, revWalk, oi);
      bu.addOp(change.getId(), inserter
          .setMessage("Uploaded patch set " + newPatchSetId.get() + ".")
          .setDraft(current.isDraft())
          .setUploader(identifiedUser.getAccountId()));
      bu.execute();
    }
    return change.getId();
  }

  private Change createNewChange(Repository git, RevWalk revWalk,
      ObjectInserter oi, Change.Key changeKey, Project.NameKey project,
      Ref destRef, CodeReviewCommit cherryPickCommit, RefControl refControl,
      IdentifiedUser identifiedUser, String topic, Branch.NameKey sourceBranch)
      throws RestApiException, UpdateException, OrmException {
    Change change =
        new Change(changeKey, new Change.Id(db.get().nextChangeId()),
            identifiedUser.getAccountId(), new Branch.NameKey(project,
                destRef.getName()), TimeUtil.nowTs());
    change.setTopic(topic);
    ChangeInserter ins = changeInserterFactory.create(
          refControl, change, cherryPickCommit)
        .setValidatePolicy(CommitValidators.Policy.GERRIT);

    ins.setMessage(
        messageForDestinationChange(ins.getPatchSet().getId(), sourceBranch));
    try (BatchUpdate bu = batchUpdateFactory.create(
        db.get(), change.getProject(), identifiedUser, TimeUtil.nowTs())) {
      bu.setRepository(git, revWalk, oi);
      bu.insertChange(ins);
      bu.execute();
    }
    return ins.getChange();
  }

  private void addMessageToSourceChange(Change change, PatchSet.Id patchSetId,
      String destinationBranch, CodeReviewCommit cherryPickCommit,
      IdentifiedUser identifiedUser, RefControl refControl)
          throws OrmException, IOException {
    ChangeMessage changeMessage = new ChangeMessage(
        new ChangeMessage.Key(
            patchSetId.getParentKey(), ChangeUtil.messageUUID(db.get())),
            identifiedUser.getAccountId(), TimeUtil.nowTs(), patchSetId);
    StringBuilder sb = new StringBuilder("Patch Set ")
        .append(patchSetId.get())
        .append(": Cherry Picked")
        .append("\n\n")
        .append("This patchset was cherry picked to branch ")
        .append(destinationBranch)
        .append(" as commit ")
        .append(cherryPickCommit.getId().getName());
    changeMessage.setMessage(sb.toString());

    ChangeControl ctl = refControl.getProjectControl().controlFor(change);
    ChangeUpdate update = updateFactory.create(ctl, TimeUtil.nowTs());
    changeMessagesUtil.addChangeMessage(db.get(), update, changeMessage);
    update.commit();
  }

  private String messageForDestinationChange(PatchSet.Id patchSetId,
      Branch.NameKey sourceBranch) {
    return new StringBuilder("Patch Set ")
      .append(patchSetId.get())
      .append(": Cherry Picked from branch ")
      .append(sourceBranch.getShortName())
      .append(".")
      .toString();
  }
}
