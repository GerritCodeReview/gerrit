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
import static com.google.gerrit.server.edit.ChangeEditUtil.editRefName;
import static com.google.gerrit.server.edit.ChangeEditUtil.editRefPrefix;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
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

  private static enum TreeOperation {
    CHANGE_ENTRY,
    DELETE_ENTRY,
    RESTORE_ENTRY
  }
  private final TimeZone tz;
  private final GitRepositoryManager gitManager;
  private final Provider<CurrentUser> currentUser;

  @Inject
  ChangeEditModifier(@GerritPersonIdent PersonIdent gerritIdent,
      GitRepositoryManager gitManager,
      Provider<CurrentUser> currentUser) {
    this.gitManager = gitManager;
    this.currentUser = currentUser;
    this.tz = gerritIdent.getTimeZone();
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
   */
  public RefUpdate.Result createEdit(Change change, PatchSet ps)
      throws AuthException, IOException, ResourceConflictException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    IdentifiedUser me = (IdentifiedUser) currentUser.get();
    Repository repo = gitManager.openRepository(change.getProject());
    String refPrefix = editRefPrefix(me.getAccountId(), change.getId());

    try {
      Map<String, Ref> refs = repo.getRefDatabase().getRefs(refPrefix);
      if (!refs.isEmpty()) {
        throw new ResourceConflictException("edit already exists");
      }

      RevWalk rw = new RevWalk(repo);
      ObjectInserter inserter = repo.newObjectInserter();
      try {
        RevCommit base = rw.parseCommit(ObjectId.fromString(
            ps.getRevision().get()));
        RevCommit changeBase = base.getParent(0);
        ObjectId commit = createCommit(me, inserter, base, changeBase, base.getTree());
        inserter.flush();
        String editRefName = editRefName(me.getAccountId(), change.getId(),
            ps.getId());
        return update(repo, me, editRefName, rw, ObjectId.zeroId(), commit);
      } finally {
        rw.release();
        inserter.release();
      }
    } finally {
      repo.close();
    }
  }

  /**
   * Rebase change edit on latest patch set
   *
   * @param edit change edit that contains edit to rebase
   * @param current patch set to rebase the edit on
   * @throws AuthException
   * @throws InvalidChangeOperationException
   * @throws IOException
   */
  public void rebaseEdit(ChangeEdit edit, PatchSet current)
      throws AuthException, InvalidChangeOperationException, IOException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    Change change = edit.getChange();
    IdentifiedUser me = (IdentifiedUser) currentUser.get();
    String refName = editRefName(me.getAccountId(), change.getId(),
        current.getId());
    Repository repo = gitManager.openRepository(change.getProject());
    try {
      RevWalk rw = new RevWalk(repo);
      BatchRefUpdate ru = repo.getRefDatabase().newBatchUpdate();
      ObjectInserter inserter = repo.newObjectInserter();
      try {
        RevCommit editCommit = edit.getEditCommit();
        if (editCommit.getParentCount() == 0) {
          throw new InvalidChangeOperationException(
              "Rebase edit against root commit not implemented");
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
          throw new InvalidChangeOperationException("merge conflict");
        }
      } finally {
        rw.release();
        inserter.release();
      }
    } finally {
      repo.close();
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
   * @throws IOException, UnchangedCommitMessage
   */
  public RefUpdate.Result modifyMessage(ChangeEdit edit, String msg)
      throws AuthException, InvalidChangeOperationException, IOException,
      UnchangedCommitMessage {
    checkState(!Strings.isNullOrEmpty(msg), "message cannot be null");
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    IdentifiedUser me = (IdentifiedUser) currentUser.get();
    Repository repo = gitManager.openRepository(edit.getChange().getProject());
    try {
      RevWalk rw = new RevWalk(repo);
      ObjectInserter inserter = repo.newObjectInserter();
      try {
        String refName = edit.getRefName();
        RevCommit prevEdit = edit.getEditCommit();
        if (prevEdit.getParentCount() == 0) {
          throw new InvalidChangeOperationException(
              "Modify edit against root commit not implemented");
        }

        if (prevEdit.getFullMessage().equals(msg)) {
          throw new UnchangedCommitMessage(
              "New commit message cannot be same as existing commit message");
        }

        ObjectId commit = createCommit(me, inserter, prevEdit,
            rw.parseCommit(prevEdit.getParent(0)),
            prevEdit.getTree(),
            msg);
        inserter.flush();
        return update(repo, me, refName, rw, prevEdit, commit);
      } finally {
        rw.release();
        inserter.release();
      }
    } finally {
      repo.close();
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
      String file, byte[] content) throws AuthException,
      InvalidChangeOperationException, IOException {
    return modify(TreeOperation.CHANGE_ENTRY, edit, file, content);
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
    return modify(TreeOperation.DELETE_ENTRY, edit, file, null);
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
    return modify(TreeOperation.RESTORE_ENTRY, edit, file, null);
  }

  private RefUpdate.Result modify(TreeOperation op,
      ChangeEdit edit, String file, byte[] content)
      throws AuthException, IOException, InvalidChangeOperationException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    IdentifiedUser me = (IdentifiedUser) currentUser.get();
    Repository repo = gitManager.openRepository(edit.getChange().getProject());
    try {
      RevWalk rw = new RevWalk(repo);
      ObjectInserter inserter = repo.newObjectInserter();
      ObjectReader reader = repo.newObjectReader();
      try {
        String refName = edit.getRefName();
        RevCommit prevEdit = edit.getEditCommit();
        if (prevEdit.getParentCount() == 0) {
          throw new InvalidChangeOperationException(
              "Modify edit against root commit not implemented");
        }

        RevCommit base = prevEdit.getParent(0);
        base = rw.parseCommit(base);
        ObjectId newTree = writeNewTree(op, rw, inserter,
            prevEdit, reader, file, content, base);
        if (ObjectId.equals(newTree, prevEdit.getTree())) {
          throw new InvalidChangeOperationException("no changes were made");
        }

        ObjectId commit = createCommit(me, inserter, prevEdit, base, newTree);
        inserter.flush();
        return update(repo, me, refName, rw, prevEdit, commit);
      } finally {
        rw.release();
        inserter.release();
        reader.release();
      }
    } finally {
      repo.close();
    }
  }

  private ObjectId createCommit(IdentifiedUser me, ObjectInserter inserter,
      RevCommit prevEdit, RevCommit base, ObjectId tree) throws IOException {
    return createCommit(me, inserter, prevEdit, base, tree,
        prevEdit.getFullMessage());
  }

  private ObjectId createCommit(IdentifiedUser me, ObjectInserter inserter,
      RevCommit prevEdit, RevCommit base, ObjectId tree, String msg)
      throws IOException {
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(tree);
    builder.setParentIds(base);
    builder.setAuthor(prevEdit.getAuthorIdent());
    builder.setCommitter(getCommitterIdent(me));
    builder.setMessage(msg);
    return inserter.insert(builder);
  }

  private RefUpdate.Result update(Repository repo, IdentifiedUser me,
      String refName, RevWalk rw, ObjectId oldObjectId, ObjectId newEdit)
      throws IOException {
    RefUpdate ru = repo.updateRef(refName);
    ru.setExpectedOldObjectId(oldObjectId);
    ru.setNewObjectId(newEdit);
    ru.setRefLogIdent(getRefLogIdent(me));
    ru.setForceUpdate(true);
    RefUpdate.Result res = ru.update(rw);
    if (res != RefUpdate.Result.NEW &&
        res != RefUpdate.Result.FORCED) {
      throw new IOException("update failed: " + ru);
    }
    return res;
  }

  private static ObjectId writeNewTree(TreeOperation op, RevWalk rw,
      ObjectInserter ins, RevCommit prevEdit, ObjectReader reader,
      String fileName, byte[] content, RevCommit base)
      throws IOException, InvalidChangeOperationException {
    DirCache newTree = createTree(reader, prevEdit);
    editTree(
        op,
        rw,
        base,
        newTree.editor(),
        ins,
        fileName,
        content);
    return newTree.writeTree(ins);
  }

  private static void editTree(TreeOperation op, RevWalk rw, RevCommit base,
      DirCacheEditor dce, ObjectInserter ins, String path, byte[] content)
      throws IOException, InvalidChangeOperationException {
    switch (op) {
      case DELETE_ENTRY:
        dce.add(new DeletePath(path));
        break;
      case CHANGE_ENTRY:
      case RESTORE_ENTRY:
        dce.add(getPathEdit(op, rw, base, path, ins, content));
        break;
    }
    dce.finish();
  }

  private static PathEdit getPathEdit(TreeOperation op, RevWalk rw,
      RevCommit base, String path, ObjectInserter ins, byte[] content)
      throws IOException, InvalidChangeOperationException {
    final ObjectId oid = op == TreeOperation.CHANGE_ENTRY
        ? ins.insert(Constants.OBJ_BLOB, content)
        : getObjectIdForRestoreOperation(rw, base, path);
    return new PathEdit(path) {
      @Override
      public void apply(DirCacheEntry ent) {
        ent.setFileMode(FileMode.REGULAR_FILE);
        ent.setObjectId(oid);
      }
    };
  }

  private static ObjectId getObjectIdForRestoreOperation(RevWalk rw,
      RevCommit base, String path)
      throws IOException, InvalidChangeOperationException {
    TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), path,
        base.getTree().getId());
    // If the file does not exist in the base commit, try to restore it
    // from the base's parent commit.
    if (tw == null && base.getParentCount() == 1) {
      tw = TreeWalk.forPath(rw.getObjectReader(), path,
          rw.parseCommit(base.getParent(0)).getTree().getId());
    }
    if (tw == null) {
      throw new InvalidChangeOperationException(String.format(
          "cannot restore path %s: missing in base revision %s",
          path, base.abbreviate(8).name()));
    }
    return tw.getObjectId(0);
  }

  private static DirCache createTree(ObjectReader reader, RevCommit prevEdit)
      throws IOException {
    DirCache dc = DirCache.newInCore();
    DirCacheBuilder b = dc.builder();
    b.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, prevEdit.getTree()
        .getId());
    b.finish();
    return dc;
  }

  private PersonIdent getCommitterIdent(IdentifiedUser user) {
    return user.newCommitterIdent(TimeUtil.nowTs(), tz);
  }

  private PersonIdent getRefLogIdent(IdentifiedUser user) {
    return user.newRefLogIdent(TimeUtil.nowTs(), tz);
  }
}
