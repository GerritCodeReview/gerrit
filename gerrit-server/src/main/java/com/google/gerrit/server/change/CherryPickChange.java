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

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.ChangeIdUtil;

import java.io.IOException;
import java.util.List;

public class CherryPickChange {

  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private final ReviewDb db;
  private final GitRepositoryManager gitManager;
  private final PersonIdent myIdent;
  private final IdentifiedUser currentUser;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  final MergeUtil.Factory mergeUtilFactory;

  @Inject
  CherryPickChange(final ReviewDb db, @GerritPersonIdent final PersonIdent myIdent,
      final GitRepositoryManager gitManager, final IdentifiedUser currentUser,
      final CommitValidators.Factory commitValidatorsFactory,
      final ChangeInserter.Factory changeInserterFactory,
      final PatchSetInserter.Factory patchSetInserterFactory,
      final MergeUtil.Factory mergeUtilFactory) {
    this.db = db;
    this.gitManager = gitManager;
    this.myIdent = myIdent;
    this.currentUser = currentUser;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.changeInserterFactory = changeInserterFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
  }

  public Change.Id cherryPick(final PatchSet.Id patchSetId,
      final String message, final String destinationBranch,
      final RefControl refControl) throws NoSuchChangeException,
      EmailException, OrmException, MissingObjectException,
      IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException, MergeException {

    final Change.Id changeId = patchSetId.getParentKey();
    final PatchSet patch = db.patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }
    if (destinationBranch == null || destinationBranch.length() == 0) {
      throw new InvalidChangeOperationException(
          "Cherry Pick: Destination branch cannot be null or empty");
    }

    Project.NameKey project = db.changes().get(changeId).getProject();
    final Repository git;
    try {
      git = gitManager.openRepository(project);
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
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
            currentUser.newCommitterIdent(myIdent.getWhen(),
                myIdent.getTimeZone());

        final ObjectId computedChangeId =
            ChangeIdUtil
                .computeChangeId(commitToCherryPick.getTree(), mergeTip,
                    commitToCherryPick.getAuthorIdent(), myIdent, message);
        String commitMessage =
            ChangeIdUtil.insertId(message, computedChangeId).trim() + '\n';

        RevCommit cherryPickCommit;
        ObjectInserter oi = git.newObjectInserter();
        try {
          ProjectState projectState = refControl.getProjectControl().getProjectState();
          cherryPickCommit =
              mergeUtilFactory.create(projectState).createCherryPickFromCommit(git, oi, mergeTip,
                  commitToCherryPick, committerIdent, commitMessage, revWalk);
        } finally {
          oi.close();
        }

        if (cherryPickCommit == null) {
          throw new MergeException("Cherry pick failed");
        }

        Change.Key changeKey;
        final List<String> idList = cherryPickCommit.getFooterLines(CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          changeKey = new Change.Key(idStr);
        } else {
          changeKey = new Change.Key("I" + computedChangeId.name());
        }

        List<Change> destChanges =
            db.changes()
                .byBranchKey(
                    new Branch.NameKey(db.changes().get(changeId).getProject(),
                        destRef.getName()), changeKey).toList();

        if (destChanges.size() > 1) {
          throw new InvalidChangeOperationException("Several changes with key "
              + changeKey + " reside on the same branch. "
              + "Cannot create a new patch set.");
        } else if (destChanges.size() == 1) {
          // The change key exists on the destination branch. The cherry pick
          // will be added as a new patch set.
          return insertPatchSet(git, revWalk, destChanges.get(0), patchSetId,
              cherryPickCommit, refControl, currentUser);
        } else {
          // Change key not found on destination branch. We can create a new
          // change.
          return createNewChange(git, revWalk, changeKey, project, patchSetId, destRef,
              cherryPickCommit, refControl);
        }
      } finally {
        revWalk.close();
      }
    } finally {
      git.close();
    }
  }

  private Change.Id insertPatchSet(Repository git, RevWalk revWalk, Change change,
      PatchSet.Id patchSetId, RevCommit cherryPickCommit,
      RefControl refControl, IdentifiedUser uploader)
      throws InvalidChangeOperationException, IOException, OrmException,
      NoSuchChangeException {
    final ChangeControl changeControl =
        refControl.getProjectControl().controlFor(change);
    final PatchSetInserter inserter = patchSetInserterFactory
        .create(git, revWalk, changeControl, cherryPickCommit);
    final PatchSet.Id newPatchSetId = inserter.getPatchSetId();
    final PatchSet current = db.patchSets().get(change.currentPatchSetId());
    inserter
      .setMessage("Uploaded patch set " + newPatchSetId.get() + ".")
      .setDraft(current.isDraft())
      .setUploader(uploader.getAccountId())
      .setCopyLabels(true)
      .insert();
    return change.getId();
  }

  private Change.Id createNewChange(Repository git, RevWalk revWalk,
      Change.Key changeKey, Project.NameKey project, PatchSet.Id patchSetId,
      Ref destRef, RevCommit cherryPickCommit, RefControl refControl)
      throws OrmException, InvalidChangeOperationException, IOException {
    Change change =
        new Change(changeKey, new Change.Id(db.nextChangeId()),
            currentUser.getAccountId(), new Branch.NameKey(project,
                destRef.getName()), TimeUtil.nowTs());
    ChangeInserter ins =
        changeInserterFactory.create(refControl, change, cherryPickCommit);
    PatchSet newPatchSet = ins.getPatchSet();

    CommitValidators commitValidators =
        commitValidatorsFactory.create(refControl, new NoSshInfo(), git);
    CommitReceivedEvent commitReceivedEvent =
        new CommitReceivedEvent(new ReceiveCommand(ObjectId.zeroId(),
            cherryPickCommit.getId(), newPatchSet.getRefName()), refControl
            .getProjectControl().getProject(), refControl.getRefName(),
            cherryPickCommit, currentUser);

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

    ins.setMessage(buildChangeMessage(patchSetId, change, cherryPickCommit))
        .insert();

    return change.getId();
  }

  private ChangeMessage buildChangeMessage(PatchSet.Id patchSetId, Change dest,
      RevCommit cherryPickCommit) throws OrmException {
    ChangeMessage cmsg = new ChangeMessage(
        new ChangeMessage.Key(
            patchSetId.getParentKey(), ChangeUtil.messageUUID(db)),
        currentUser.getAccountId(), TimeUtil.nowTs(), patchSetId);
    String destBranchName = dest.getDest().get();
    StringBuilder msgBuf = new StringBuilder("Patch Set ")
        .append(patchSetId.get())
        .append(": Cherry Picked")
        .append("\n\n")
        .append("This patchset was cherry picked to branch ")
        .append(destBranchName.substring(
            destBranchName.indexOf("refs/heads/") + "refs/heads/".length()))
        .append(" as commit ")
        .append(cherryPickCommit.getId().getName());
    cmsg.setMessage(msgBuf.toString());
    return cmsg;
  }
}
