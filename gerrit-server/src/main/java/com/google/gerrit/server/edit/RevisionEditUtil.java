// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.edit;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Utility functions to manipulate revision edits.
 * <p>
 * This class contains methods to retrieve, publish and delete edits.
 * For changing edits see {@link RevisionEditModifier}.
 */
@Singleton
public class RevisionEditUtil {
  private final GitRepositoryManager gitManager;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final PersonIdent myIdent;
  private final Provider<ReviewDb> db;
  private final Provider<IdentifiedUser> user;

  @Inject
  public RevisionEditUtil(GitRepositoryManager gitManager,
      PatchSetInserter.Factory patchSetInserterFactory,
      ChangeControl.GenericFactory changeControlFactory,
      @GerritPersonIdent PersonIdent myIdent,
      Provider<ReviewDb> db,
      Provider<IdentifiedUser> user) {
    this.gitManager = gitManager;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.changeControlFactory = changeControlFactory;
    this.myIdent = myIdent;
    this.db = db;
    this.user = user;
  }

  /**
   * Retrieve edits for a change and user. Max one change can exist
   * per user and change.
   * @param change
   * @return revision edits wrapped inside Optional when edit for this
   * change exists or absent otherwise
   * @throws AuthException
   * @throws IOException
   */
  public Optional<RevisionEdit> byChange(Change change)
      throws AuthException, IOException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    Repository repo = gitManager.openRepository(change.getProject());
    try {
      IdentifiedUser identifiedUser = (IdentifiedUser) user.get();
      Ref ref = repo.getRefDatabase().getRef(editRefName(
          identifiedUser.getAccountId(), change.getId()));
      if (ref == null) {
        return Optional.absent();
      }
      return Optional.of(new RevisionEdit(identifiedUser, change, ref));
    } finally {
      repo.close();
    }
  }

  /**
   * Promote revision edit to patch set, by squashing the edit into
   * its parent.
   * <p>
   * @param edit revision edit to publish
   * @throws AuthException
   * @throws NoSuchChangeException
   * @throws IOException
   * @throws InvalidChangeOperationException
   * @throws OrmException
   * @throws ResourceConflictException
   */
  public void publish(RevisionEdit edit) throws AuthException,
      NoSuchChangeException, IOException, InvalidChangeOperationException,
      OrmException, ResourceConflictException {
    Change change = edit.getChange();
    Repository repo = gitManager.openRepository(change.getProject());
    try {
      RevWalk rw = new RevWalk(repo);
      ObjectInserter inserter = repo.newObjectInserter();
      try {
        RevCommit editCommit = rw.parseCommit(edit.getRef().getObjectId());
        if (editCommit == null) {
          throw new NoSuchChangeException(change.getId());
        }

        PatchSet basePatchSet = getBasePatchSet(edit, editCommit);
        if (!basePatchSet.getId().equals(change.currentPatchSetId())) {
          throw new ResourceConflictException(
              "only edit for current patch set can be published");
        }

        insertPatchSet(edit, change, repo, rw, basePatchSet,
            squashEdit(repo, rw, inserter, editCommit, basePatchSet));
      } finally {
        inserter.release();
        rw.release();
      }

      deleteRef(repo, edit);
    } finally {
      repo.close();
    }
  }

  /**
   * Delete revision edit.
   * @param edit revision edit to delete
   * @throws IOException
   */
  public void delete(RevisionEdit edit)
      throws IOException {
    Change change = edit.getChange();
    Repository repo = gitManager.openRepository(change.getProject());
    try {
      deleteRef(repo, edit);
    } finally {
      repo.close();
    }
  }

  /**
   * Retrieve base patch set the edit was created on.
   * <p>
   * @param edit revision edit to retrieve base patch set for
   * @param commit revision edit commit
   * @return parent patch set of the edit
   */
  public PatchSet getBasePatchSet(RevisionEdit edit, RevCommit commit)
      throws IOException {
    RevCommit parentCommit = commit.getParent(0);
    RevId parentRev = new RevId(ObjectId.toString(parentCommit.getId()));
    try {
      return Iterables.getOnlyElement(db.get().patchSets()
          .byRevision(parentRev).toList());
    } catch (OrmException e) {
      throw new IOException(e);
    }
  }

  /**
   * Retrieve git commit for revision edit.
   * @param edit to retrieve commit for
   * @return RevCommit
   * @throws NoSuchChangeException
   * @throws IOException
   */
  public RevCommit getCommit(RevisionEdit edit) throws NoSuchChangeException,
      IOException {
    Change change = edit.getChange();
    Repository repo = gitManager.openRepository(change.getProject());
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        RevCommit editCommit = rw.parseCommit(edit.getRef().getObjectId());
        if (editCommit == null) {
          throw new NoSuchChangeException(change.getId());
        }
        return editCommit;
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  /**
   * Retrieve parent git commit for revision edit.
   * @param edit to retrieve parent commit for
   * @return RevCommit
   * @throws NoSuchChangeException
   * @throws IOException
   */
  public RevCommit getParentCommit(RevisionEdit edit)
      throws NoSuchChangeException, IOException {
    Change change = edit.getChange();
    Repository repo = gitManager.openRepository(change.getProject());
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        RevCommit editCommit = rw.parseCommit(edit.getRef().getObjectId());
        if (editCommit == null) {
          throw new NoSuchChangeException(change.getId());
        }
        checkState(editCommit.getParentCount() == 1);
        return rw.parseCommit(editCommit.getParent(0));
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  /**
   * Returns reference for this revision edit with sharded user and change number:
   * refs/users/UU/UUUU/edit-CCCC.
   * @param accountId accout id
   * @param changeId change number
   * @return reference for this revision edit
   */
  static String editRefName(Account.Id accountId, Change.Id changeId) {
    return String.format("%s/edit-%d",
        RefNames.refsUsers(accountId),
        changeId.get());
  }

  private RevCommit squashEdit(Repository repo, RevWalk rw,
      ObjectInserter inserter, RevCommit editCommit, PatchSet basePatchSet)
      throws IOException, ResourceConflictException {
    ObjectId p = ObjectId.fromString(basePatchSet.getRevision().get());
    RevCommit mergeTip = rw.parseCommit(p);
    ThreeWayMerger m = MergeUtil.newThreeWayMerger(repo, inserter,
        MergeStrategy.RECURSIVE.getName());
    RevCommit squashed;
    if (m.merge(new AnyObjectId[] {mergeTip, editCommit})) {
      squashed = writeMergeCommit(myIdent, rw, inserter,
          mergeTip, m.getResultTreeId(), editCommit);
    } else {
      throw new ResourceConflictException("cannot squash edit");
    }
    return squashed;
  }

  private void insertPatchSet(RevisionEdit edit, Change change,
      Repository repo, RevWalk rw, PatchSet basePatchSet, RevCommit squashed)
      throws NoSuchChangeException, InvalidChangeOperationException,
      OrmException, IOException {
    PatchSet ps = new PatchSet(new PatchSet.Id(change.getId(),
        change.currentPatchSetId().get() + 1));
    ps.setRevision(new RevId(ObjectId.toString(squashed)));
    ps.setUploader(edit.getUser().getAccountId());
    ps.setCreatedOn(TimeUtil.nowTs());

    PatchSetInserter insr =
        patchSetInserterFactory.create(repo, rw,
            changeControlFactory.controlFor(change, edit.getUser()),
            squashed);
    insr.setPatchSet(ps)
        .setMessage(
            String.format("Patch Set %d: New edit was published",
                basePatchSet.getPatchSetId()))
        .insert();
  }

  private void deleteRef(Repository repo, RevisionEdit edit)
      throws IOException {
    String refName = edit.getRefName();
    RefUpdate ru = repo.updateRef(refName, true);
    ru.setForceUpdate(true);
    RefUpdate.Result result = ru.delete();
    switch (result) {
      case FORCED:
      case NEW:
      case NO_CHANGE:
        break;
      default:
        throw new IOException(String.format("Failed to delete ref %s: %s",
            refName, result));
    }
  }

  private RevCommit writeMergeCommit(PersonIdent myIdent2, RevWalk rw,
      ObjectInserter inserter, RevCommit mergeTip, ObjectId treeId,
      RevCommit editCommit) throws IOException,
      MissingObjectException, IncorrectObjectTypeException {
    CommitBuilder mergeCommit = new CommitBuilder();
    mergeCommit.setTreeId(treeId);
    mergeCommit.setParentIds(mergeTip.getParent(0));
    mergeCommit.setAuthor(mergeTip.getAuthorIdent());
    mergeCommit.setCommitter(editCommit.getCommitterIdent());
    mergeCommit.setMessage(mergeTip.getFullMessage());

    return rw.parseCommit(commit(inserter, mergeCommit));
  }

  private ObjectId commit(final ObjectInserter inserter,
      final CommitBuilder mergeCommit) throws IOException,
      UnsupportedEncodingException {
    ObjectId id = inserter.insert(mergeCommit);
    inserter.flush();
    return id;
  }
}
