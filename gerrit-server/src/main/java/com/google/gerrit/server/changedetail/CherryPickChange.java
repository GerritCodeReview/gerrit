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

package com.google.gerrit.server.changedetail;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
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
import org.eclipse.jgit.util.ChangeIdUtil;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

public class CherryPickChange {

  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  public interface Factory {
    CherryPickChange create();
  }

  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ReviewDb db;
  private final GitRepositoryManager gitManager;
  private final PersonIdent myIdent;
  private final GitReferenceUpdated replication;
  private final ChangeHookRunner hooks;
  private final IdentifiedUser currentUser;

  @Inject
  CherryPickChange(final PatchSetInfoFactory patchSetInfoFactory, final ReviewDb db,
      @GerritPersonIdent final PersonIdent myIdent,
      final GitRepositoryManager gitManager,
      final GitReferenceUpdated replication, final ChangeHookRunner hooks,
      final IdentifiedUser currentUser) {
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.db = db;
    this.gitManager = gitManager;
    this.myIdent = myIdent;
    this.replication = replication;
    this.hooks = hooks;
    this.currentUser = currentUser;
  }

  public Change.Id cherryPick(final PatchSet.Id patchSetId,
      final String message, final String destinationBranch)
      throws NoSuchChangeException, EmailException, OrmException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
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

    final Repository git;
    try {
      git = gitManager.openRepository(db.changes().get(changeId).getProject());
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    }

    try {
      RevWalk revWalk = null;
      try {
        revWalk = new RevWalk(git);

        Ref destRef = git.getRef(destinationBranch);
        if (destRef == null) {
          throw new InvalidChangeOperationException("Branch "
              + destinationBranch + " does not exist.");
        }

        final RevCommit mergeTip = revWalk.parseCommit(destRef.getObjectId());

        RevCommit commitToCherryPick =
            revWalk.parseCommit(ObjectId.fromString(patch.getRevision().get()));

        PersonIdent authorIdent =
            currentUser.newCommitterIdent(myIdent.getWhen(),
                myIdent.getTimeZone());

        RevCommit cherryPickCommit = null;
        ObjectInserter oi = null;
        try {
          oi = git.newObjectInserter();

          cherryPickCommit =
              MergeUtil.createCherryPickFromCommit(git, oi, mergeTip,
                  commitToCherryPick, authorIdent, message, revWalk, true);
        } finally {
          oi.release();
        }

        if (cherryPickCommit == null) {
          throw new MergeException(
              "Could not create a merge commit during the cherry pick");
        }

        Change.Key changeKey = null;
        final List<String> idList = cherryPickCommit.getFooterLines(CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          changeKey = new Change.Key(idStr);
        } else {
          final ObjectId computedChangeId =
              ChangeIdUtil.computeChangeId(commitToCherryPick.getTree(),
                  commitToCherryPick, authorIdent, myIdent, message);

          changeKey = new Change.Key("I" + computedChangeId.name());
        }

        List<Change> destChanges =
            db.changes()
                .byBranchKey(
                    new Branch.NameKey(db.changes().get(changeId).getProject(),
                        destRef.getName()), changeKey).toList();

        Change change = null;
        boolean createNewChange = false;

        if (destChanges.size() > 1) {
          throw new InvalidChangeOperationException("Several changes with key "
              + changeKey + " resides on the same branch. "
              + "Cannot create a new patch set.");
        } else if (destChanges.size() == 1) {
          // The change key exists on the destination branch. The cherry pick
          // will be added as a new patch set.
          change = destChanges.get(0);
        } else {
          // Change key not found on destination branch. We can create a new
          // change.
          change =
              new Change(changeKey, new Change.Id(db.nextChangeId()),
                  currentUser.getAccountId(), new Branch.NameKey(db.changes()
                      .get(changeId).getProject(), destRef.getName()));
          createNewChange = true;
        }

        change.nextPatchSetId();
        final PatchSet newPatchSet = new PatchSet(change.currPatchSetId());
        newPatchSet.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        newPatchSet.setUploader(change.getOwner());
        newPatchSet.setRevision(new RevId(cherryPickCommit.name()));

        final RefUpdate ru = git.updateRef(newPatchSet.getRefName());
        ru.setExpectedOldObjectId(ObjectId.zeroId());
        ru.setNewObjectId(cherryPickCommit);
        ru.disableRefLog();
        if (ru.update(revWalk) != RefUpdate.Result.NEW) {
          throw new IOException(String.format(
              "Failed to create ref %s in %s: %s", newPatchSet.getRefName(),
              change.getDest().getParentKey().get(), ru.getResult()));
        }
        replication.fire(change.getProject(), ru.getName());

        db.changes().beginTransaction(change.getId());
        try {
          if (createNewChange) {
            cherryPickToNewChange(change, newPatchSet, cherryPickCommit);
          } else {
            cherryPickToNewPatchSet(change, newPatchSet, cherryPickCommit);
          }

          final ChangeMessage cmsg =
              new ChangeMessage(new ChangeMessage.Key(changeId,
                  ChangeUtil.messageUUID(db)), currentUser.getAccountId(),
                  patchSetId);
          final StringBuilder msgBuf =
              new StringBuilder("Patch Set " + patchSetId.get()
                  + ": Cherry Picked");
          msgBuf.append("\n\n");
          msgBuf.append("This patchset was cherry picked to change: "
              + change.getKey().get());
          cmsg.setMessage(msgBuf.toString());
          db.changeMessages().insert(Collections.singleton(cmsg));

          db.commit();

          hooks.doPatchsetCreatedHook(change, newPatchSet, db);

          return change.getId();
        } finally {
          db.rollback();
        }
      } finally {
        revWalk.release();
      }
    } finally {
      git.close();
    }
  }

  private void cherryPickToNewPatchSet(Change change, final PatchSet patchSet,
      final RevCommit cherryPickCommit) throws OrmException,
      InvalidChangeOperationException {
    final PatchSet.Id currentPatchSetId = change.currentPatchSetId();

    Change updatedChange =
        db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus().isOpen()) {
              change.updateNumberOfPatchSets(patchSet.getPatchSetId());
              return change;
            } else {
              return null;
            }
          }
        });
    if (updatedChange != null) {
      change = updatedChange;
    } else {
      throw new InvalidChangeOperationException(String.format(
          "Change %s is closed", change.getId()));
    }

    ChangeUtil.insertAncestors(db, patchSet.getId(), cherryPickCommit);
    db.patchSets().insert(Collections.singleton(patchSet));

    updatedChange =
        db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus().isClosed()) {
              return null;
            }
            if (!change.currentPatchSetId().equals(currentPatchSetId)) {
              return null;
            }
            if (change.getStatus() != Change.Status.DRAFT) {
              change.setStatus(Change.Status.NEW);
            }
            change.setLastSha1MergeTested(null);
            change.setCurrentPatchSet(patchSetInfoFactory.get(cherryPickCommit,
                patchSet.getId()));
            ChangeUtil.updated(change);
            return change;
          }
        });
    if (updatedChange != null) {
      change = updatedChange;
    } else {
      throw new InvalidChangeOperationException(String.format(
          "Change %s was modified", change.getId()));
    }
  }

  private void cherryPickToNewChange(Change change, PatchSet patchSet,
      RevCommit cherryPickCommit) throws OrmException {
    change.setCurrentPatchSet(patchSetInfoFactory.get(cherryPickCommit,
        patchSet.getId()));

    ChangeUtil.insertAncestors(db, patchSet.getId(), cherryPickCommit);
    db.patchSets().insert(Collections.singleton(patchSet));
    ChangeUtil.updated(change);
    db.changes().insert(Collections.singleton(change));
  }
}
