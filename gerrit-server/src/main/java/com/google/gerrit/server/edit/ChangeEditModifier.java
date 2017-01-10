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

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.edit.tree.DeleteFileModification;
import com.google.gerrit.server.edit.tree.RenameFileModification;
import com.google.gerrit.server.edit.tree.RestoreFileModification;
import com.google.gerrit.server.edit.tree.TreeCreator;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.TimeZone;

/**
 * Utility functions to manipulate change edits.
 * <p>
 * This class contains methods to modify edit's content.
 * For retrieving, publishing and deleting edit see
 * {@link ChangeEditUtil}.
 * <p>
 */
@Singleton
public class ChangeEditModifier {

  private final TimeZone tz;
  private final GitRepositoryManager gitManager;
  private final ChangeIndexer indexer;
  private final Provider<ReviewDb> reviewDb;
  private final Provider<CurrentUser> currentUser;
  private final ChangeControl.GenericFactory changeControlFactory;

  @Inject
  ChangeEditModifier(@GerritPersonIdent PersonIdent gerritIdent,
      GitRepositoryManager gitManager,
      ChangeIndexer indexer,
      Provider<ReviewDb> reviewDb,
      Provider<CurrentUser> currentUser,
      ChangeControl.GenericFactory changeControlFactory) {
    this.gitManager = gitManager;
    this.indexer = indexer;
    this.reviewDb = reviewDb;
    this.currentUser = currentUser;
    this.tz = gerritIdent.getTimeZone();
    this.changeControlFactory = changeControlFactory;
  }

  /**
   * Create new change edit.
   *
   * @param change to create change edit for
   * @param ps patch set to create change edit on
   * @return result
   * @throws AuthException
   * @throws IOException
   * @throws ResourceConflictException When change edit already
   * exists for the change
   * @throws OrmException
   */
  public RefUpdate.Result createEdit(Change change, PatchSet ps)
      throws AuthException, IOException, ResourceConflictException, OrmException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    IdentifiedUser me = currentUser.get().asIdentifiedUser();
    String refPrefix = RefNames.refsEditPrefix(me.getAccountId(), change.getId());

    try {
      ChangeControl c =
          changeControlFactory.controlFor(reviewDb.get(), change, me);
      if (!c.canAddPatchSet(reviewDb.get())) {
        return RefUpdate.Result.REJECTED;
      }
    } catch (NoSuchChangeException e) {
      return RefUpdate.Result.NO_CHANGE;
    }

    try (Repository repo = gitManager.openRepository(change.getProject())) {
      Map<String, Ref> refs = repo.getRefDatabase().getRefs(refPrefix);
      if (!refs.isEmpty()) {
        throw new ResourceConflictException("edit already exists");
      }

      try (RevWalk rw = new RevWalk(repo)) {
        ObjectId revision = ObjectId.fromString(ps.getRevision().get());
        String editRefName = RefNames.refsEdit(me.getAccountId(), change.getId(),
            ps.getId());
        Result res = update(repo, me, editRefName, rw, ObjectId.zeroId(),
            revision, TimeUtil.nowTs());
        indexer.index(reviewDb.get(), change);
        return res;
      }
    }
  }

  /**
   * Rebase change edit on latest patch set
   *
   * @param edit change edit that contains edit to rebase
   * @param current patch set to rebase the edit on
   * @throws AuthException
   * @throws ResourceConflictException thrown if rebase fails due to merge conflicts
   * @throws InvalidChangeOperationException
   * @throws IOException
   */
  public void rebaseEdit(ChangeEdit edit, PatchSet current)
      throws AuthException, ResourceConflictException,
      InvalidChangeOperationException, IOException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    Change change = edit.getChange();
    IdentifiedUser me = currentUser.get().asIdentifiedUser();
    String refName = RefNames.refsEdit(me.getAccountId(), change.getId(),
        current.getId());
    try (Repository repo = gitManager.openRepository(change.getProject());
        RevWalk rw = new RevWalk(repo);
        ObjectInserter inserter = repo.newObjectInserter()) {
      BatchRefUpdate ru = repo.getRefDatabase().newBatchUpdate();
      RevCommit editCommit = edit.getEditCommit();
      if (editCommit.getParentCount() == 0) {
        throw new InvalidChangeOperationException(
            "Rebase edit against root commit not supported");
      }
      RevCommit tip = rw.parseCommit(ObjectId.fromString(
          current.getRevision().get()));
      ThreeWayMerger m = MergeStrategy.RESOLVE.newMerger(repo, true);
      m.setObjectInserter(inserter);
      m.setBase(ObjectId.fromString(
          edit.getBasePatchSet().getRevision().get()));

      if (m.merge(tip, editCommit)) {
        ObjectId tree = m.getResultTreeId();

        CommitBuilder commit = new CommitBuilder();
        commit.setTreeId(tree);
        for (int i = 0; i < tip.getParentCount(); i++) {
          commit.addParentId(tip.getParent(i));
        }
        commit.setAuthor(editCommit.getAuthorIdent());
        commit.setCommitter(new PersonIdent(
            editCommit.getCommitterIdent(), TimeUtil.nowTs()));
        commit.setMessage(editCommit.getFullMessage());
        ObjectId newEdit = inserter.insert(commit);
        inserter.flush();

        ru.addCommand(new ReceiveCommand(ObjectId.zeroId(), newEdit,
            refName));
        ru.addCommand(new ReceiveCommand(edit.getRef().getObjectId(),
            ObjectId.zeroId(), edit.getRefName()));
        ru.execute(rw, NullProgressMonitor.INSTANCE);
        for (ReceiveCommand cmd : ru.getCommands()) {
          if (cmd.getResult() != ReceiveCommand.Result.OK) {
            throw new IOException("failed: " + cmd);
          }
        }
      } else {
        // TODO(davido): Allow to resolve conflicts inline
        throw new ResourceConflictException("merge conflict");
      }
    }
  }

  /**
   * Modify commit message in existing change edit.
   *
   * @param edit change edit
   * @param msg new commit message
   * @return result
   * @throws AuthException
   * @throws InvalidChangeOperationException
   * @throws IOException
   * @throws UnchangedCommitMessageException
   */
  public RefUpdate.Result modifyMessage(ChangeEdit edit, String msg)
      throws AuthException, InvalidChangeOperationException, IOException,
      UnchangedCommitMessageException {
    msg = msg.trim() + "\n";
    checkState(!Strings.isNullOrEmpty(msg), "message cannot be null");
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    RevCommit prevEdit = edit.getEditCommit();
    if (prevEdit.getFullMessage().equals(msg)) {
      throw new UnchangedCommitMessageException();
    }

    IdentifiedUser me = currentUser.get().asIdentifiedUser();
    Project.NameKey project = edit.getChange().getProject();
    try (Repository repo = gitManager.openRepository(project);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter inserter = repo.newObjectInserter()) {
      String refName = edit.getRefName();
      Timestamp now = TimeUtil.nowTs();
      ObjectId commit = createCommit(me, inserter, prevEdit,
          prevEdit.getTree(),
          msg, now);
      inserter.flush();
      return update(repo, me, refName, rw, prevEdit, commit, now);
    }
  }

  /**
   * Modify file in existing change edit from its base commit.
   *
   * @param edit change edit
   * @param file path to modify
   * @param content new content
   * @return result
   * @throws AuthException
   * @throws InvalidChangeOperationException
   * @throws IOException
   */
  public RefUpdate.Result modifyFile(ChangeEdit edit,
      String file, RawInput content) throws AuthException,
      InvalidChangeOperationException, IOException {
    return modify(edit, new ChangeFileContentModification(file, content));
  }

  /**
   * Delete file in existing change edit.
   *
   * @param edit change edit
   * @param file path to delete
   * @return result
   * @throws AuthException
   * @throws InvalidChangeOperationException
   * @throws IOException
   */
  public RefUpdate.Result deleteFile(ChangeEdit edit,
      String file) throws AuthException, InvalidChangeOperationException,
      IOException {
    return modify(edit, new DeleteFileModification(file));
  }

  /**
   * Rename file in existing change edit.
   *
   * @param edit change edit
   * @param file path to rename
   * @param newFile path to rename the file to
   * @return result
   * @throws AuthException
   * @throws InvalidChangeOperationException
   * @throws IOException
   */
  public RefUpdate.Result renameFile(ChangeEdit edit, String file,
      String newFile) throws AuthException, InvalidChangeOperationException,
      IOException {
    RevCommit editCommit = edit.getEditCommit();
    return modify(edit, new RenameFileModification(editCommit, file, newFile));
  }

  /**
   * Restore file in existing change edit.
   *
   * @param edit change edit
   * @param file path to restore
   * @return result
   * @throws AuthException
   * @throws InvalidChangeOperationException
   * @throws IOException
   */
  public RefUpdate.Result restoreFile(ChangeEdit edit,
      String file) throws AuthException, InvalidChangeOperationException,
      IOException {
    RevCommit editCommit = edit.getEditCommit();
    return modify(edit, new RestoreFileModification(editCommit, file));
  }

  private RefUpdate.Result modify(ChangeEdit edit,
      TreeModification treeModification) throws AuthException, IOException,
      InvalidChangeOperationException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    IdentifiedUser me = currentUser.get().asIdentifiedUser();
    Project.NameKey project = edit.getChange().getProject();
    try (Repository repo = gitManager.openRepository(project);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter inserter = repo.newObjectInserter()) {
      String refName = edit.getRefName();
      RevCommit prevEdit = edit.getEditCommit();

      TreeCreator treeCreator = new TreeCreator(prevEdit.getTree());
      treeCreator.addTreeModification(treeModification);
      ObjectId newTree = treeCreator.createNewTreeAndGetId(repo);

      if (ObjectId.equals(newTree, prevEdit.getTree())) {
        throw new InvalidChangeOperationException("no changes were made");
      }

      Timestamp now = TimeUtil.nowTs();
      ObjectId commit = createCommit(me, inserter, prevEdit, newTree, now);
      inserter.flush();
      return update(repo, me, refName, rw, prevEdit, commit, now);
    }
  }

  private ObjectId createCommit(IdentifiedUser me, ObjectInserter inserter,
      RevCommit revision, ObjectId tree, Timestamp when) throws IOException {
    return createCommit(me, inserter, revision, tree,
        revision.getFullMessage(), when);
  }

  private ObjectId createCommit(IdentifiedUser me, ObjectInserter inserter,
      RevCommit revision, ObjectId tree, String msg, Timestamp when)
      throws IOException {
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(tree);
    builder.setParentIds(revision.getParents());
    builder.setAuthor(revision.getAuthorIdent());
    builder.setCommitter(getCommitterIdent(me, when));
    builder.setMessage(msg);
    return inserter.insert(builder);
  }

  private RefUpdate.Result update(Repository repo, IdentifiedUser me,
      String refName, RevWalk rw, ObjectId oldObjectId, ObjectId newEdit,
      Timestamp when) throws IOException {
    RefUpdate ru = repo.updateRef(refName);
    ru.setExpectedOldObjectId(oldObjectId);
    ru.setNewObjectId(newEdit);
    ru.setRefLogIdent(getRefLogIdent(me, when));
    ru.setRefLogMessage("inline edit (amend)", false);
    ru.setForceUpdate(true);
    RefUpdate.Result res = ru.update(rw);
    if (res != RefUpdate.Result.NEW &&
        res != RefUpdate.Result.FORCED) {
      throw new IOException("update failed: " + ru);
    }
    return res;
  }

  private PersonIdent getCommitterIdent(IdentifiedUser user, Timestamp when) {
    return user.newCommitterIdent(when, tz);
  }

  private PersonIdent getRefLogIdent(IdentifiedUser user, Timestamp when) {
    return user.newRefLogIdent(when, tz);
  }
}
