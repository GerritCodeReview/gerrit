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

import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeConflictException;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.ssh.NoSshInfo;
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
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
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
  private final Provider<CurrentUser> currentUser;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final ChangeUpdate.Factory updateFactory;

  @Inject
  CherryPickChange(Provider<ReviewDb> db,
      Provider<InternalChangeQuery> queryProvider,
      @GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager gitManager,
      Provider<CurrentUser> currentUser,
      CommitValidators.Factory commitValidatorsFactory,
      ChangeInserter.Factory changeInserterFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtil.Factory mergeUtilFactory,
      ChangeMessagesUtil changeMessagesUtil,
      ChangeUpdate.Factory updateFactory) {
    this.db = db;
    this.queryProvider = queryProvider;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.currentUser = currentUser;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.changeInserterFactory = changeInserterFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.changeMessagesUtil = changeMessagesUtil;
    this.updateFactory = updateFactory;
  }

  public Change.Id cherryPick(Change change, PatchSet patch,
      final String message, final String destinationBranch,
      final RefControl refControl) throws NoSuchChangeException,
      OrmException, MissingObjectException,
      IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException, MergeException, UpdateException,
      RestApiException {

    if (destinationBranch == null || destinationBranch.length() == 0) {
      throw new InvalidChangeOperationException(
          "Cherry Pick: Destination branch cannot be null or empty");
    }

    Project.NameKey project = change.getProject();
    IdentifiedUser identifiedUser = (IdentifiedUser) currentUser.get();
    final Repository git;
    try {
      git = gitManager.openRepository(project);
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(change.getId(), e);
    }

    try {
      RevWalk revWalk = new RevWalk(git);
      try {
        Ref destRef = git.getRef(destinationBranch);
        if (destRef == null) {
          throw new InvalidChangeOperationException("Branch "
              + destinationBranch + " does not exist.");
        }

        final RevCommit mergeTip = revWalk.parseCommit(destRef.getObjectId());

        RevCommit commitToCherryPick =
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

        RevCommit cherryPickCommit;
        ObjectInserter oi = git.newObjectInserter();
        try {
          ProjectState projectState = refControl.getProjectControl().getProjectState();
          cherryPickCommit =
              mergeUtilFactory.create(projectState).createCherryPickFromCommit(git, oi, mergeTip,
                  commitToCherryPick, committerIdent, commitMessage, revWalk);
        } catch (MergeIdenticalTreeException | MergeConflictException e) {
          throw new MergeException("Cherry pick failed: " + e.getMessage());
        } finally {
          oi.release();
        }

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
          return insertPatchSet(git, revWalk, destChanges.get(0).change(),
              cherryPickCommit, refControl, identifiedUser);
        } else {
          // Change key not found on destination branch. We can create a new
          // change.
          Change newChange = createNewChange(git, revWalk, changeKey, project,
              destRef, cherryPickCommit, refControl,
              identifiedUser, change.getTopic());

          addMessageToSourceChange(change, patch.getId(), destinationBranch,
              cherryPickCommit, identifiedUser, refControl);

          addMessageToDestinationChange(newChange, change.getDest().getShortName(),
              identifiedUser, refControl);

          return newChange.getId();
        }
      } finally {
        revWalk.release();
      }
    } finally {
      git.close();
    }
  }

  private Change.Id insertPatchSet(Repository git, RevWalk revWalk, Change change,
      RevCommit cherryPickCommit, RefControl refControl,
      IdentifiedUser identifiedUser)
      throws InvalidChangeOperationException, IOException, OrmException,
      NoSuchChangeException, UpdateException, RestApiException {
    final ChangeControl changeControl =
        refControl.getProjectControl().controlFor(change);
    final PatchSetInserter inserter = patchSetInserterFactory
        .create(git, revWalk, changeControl, cherryPickCommit);
    final PatchSet.Id newPatchSetId = inserter.getPatchSetId();
    PatchSet current = db.get().patchSets().get(change.currentPatchSetId());
    inserter
      .setMessage("Uploaded patch set " + newPatchSetId.get() + ".")
      .setDraft(current.isDraft())
      .setUploader(identifiedUser.getAccountId())
      .insert();
    return change.getId();
  }

  private Change createNewChange(Repository git, RevWalk revWalk,
      Change.Key changeKey, Project.NameKey project,
      Ref destRef, RevCommit cherryPickCommit, RefControl refControl,
      IdentifiedUser identifiedUser, String topic)
      throws OrmException, InvalidChangeOperationException, IOException {
    Change change =
        new Change(changeKey, new Change.Id(db.get().nextChangeId()),
            identifiedUser.getAccountId(), new Branch.NameKey(project,
                destRef.getName()), TimeUtil.nowTs());
    change.setTopic(topic);
    ChangeInserter ins =
        changeInserterFactory.create(refControl, change, cherryPickCommit);
    PatchSet newPatchSet = ins.getPatchSet();

    CommitValidators commitValidators =
        commitValidatorsFactory.create(refControl, new NoSshInfo(), git);
    CommitReceivedEvent commitReceivedEvent =
        new CommitReceivedEvent(new ReceiveCommand(ObjectId.zeroId(),
            cherryPickCommit.getId(), newPatchSet.getRefName()), refControl
            .getProjectControl().getProject(), refControl.getRefName(),
            cherryPickCommit, identifiedUser);

    try {
      commitValidators.validateForGerritCommits(commitReceivedEvent);
    } catch (CommitValidationException e) {
      throw new InvalidChangeOperationException(e.getMessage());
    }

    final RefUpdate ru = git.updateRef(newPatchSet.getRefName());
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(cherryPickCommit);
    ru.disableRefLog();
    if (ru.update(revWalk) != RefUpdate.Result.NEW) {
      throw new IOException(String.format(
          "Failed to create ref %s in %s: %s", newPatchSet.getRefName(),
          change.getDest().getParentKey().get(), ru.getResult()));
    }

    ins.insert();

    return change;
  }

  private void addMessageToSourceChange(Change change, PatchSet.Id patchSetId,
      String destinationBranch, RevCommit cherryPickCommit,
      IdentifiedUser identifiedUser, RefControl refControl) throws OrmException {
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
    ChangeUpdate update = updateFactory.create(ctl, change.getCreatedOn());
    changeMessagesUtil.addChangeMessage(db.get(), update, changeMessage);
  }

  private void addMessageToDestinationChange(Change change, String sourceBranch,
      IdentifiedUser identifiedUser, RefControl refControl) throws OrmException {
    PatchSet.Id patchSetId =
        db.get().patchSets().get(change.currentPatchSetId()).getId();
    ChangeMessage changeMessage = new ChangeMessage(
        new ChangeMessage.Key(
            patchSetId.getParentKey(), ChangeUtil.messageUUID(db.get())),
            identifiedUser.getAccountId(), TimeUtil.nowTs(), patchSetId);

    StringBuilder sb = new StringBuilder("Patch Set ")
      .append(patchSetId.get())
      .append(": Cherry Picked from branch ")
      .append(sourceBranch)
      .append(".");
    changeMessage.setMessage(sb.toString());

    ChangeControl ctl = refControl.getProjectControl().controlFor(change);
    ChangeUpdate update = updateFactory.create(ctl, change.getCreatedOn());
    changeMessagesUtil.addChangeMessage(db.get(), update, changeMessage);
  }
}
