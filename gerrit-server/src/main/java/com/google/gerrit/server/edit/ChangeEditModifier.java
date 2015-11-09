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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.gerrit.common.Nullable;
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
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeIndexer;
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
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
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
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.InputStream;
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
    RENAME_ENTRY,
    RESTORE_ENTRY
  }
  private final TimeZone tz;
  private final GitRepositoryManager gitManager;
  private final ChangeIndexer indexer;
  private final Provider<ReviewDb> reviewDb;
  private final Provider<CurrentUser> currentUser;

  @Inject
  ChangeEditModifier(@GerritPersonIdent PersonIdent gerritIdent,
      GitRepositoryManager gitManager,
      ChangeIndexer indexer,
      Provider<ReviewDb> reviewDb,
      Provider<CurrentUser> currentUser) {
    this.gitManager = gitManager;
    this.indexer = indexer;
    this.reviewDb = reviewDb;
    this.currentUser = currentUser;
    this.tz = gerritIdent.getTimeZone();
  }

  /**
   * Create new change edit.
   *
   * @param change to create change edit for
   * @param base patch set to create change edit on
   * @return result
   * @throws AuthException
   * @throws IOException
   * @throws ResourceConflictException When change edit already
   * exists for the change
   */
  public RefUpdate.Result createEdit(Change change, PatchSet base)
      throws AuthException, IOException, ResourceConflictException {
    return createEdit(change, base, base);
  }

  /**
   * Create new change edit.
   *
   * @param change to create change edit for
   * @param base patch set to create change edit on
   * @param ps patch set to create cange edit for
   * @return result
   * @throws AuthException
   * @throws IOException
   * @throws ResourceConflictException When change edit already
   * exists for the change
   */
  public RefUpdate.Result createEdit(Change change, PatchSet base, PatchSet ps)
      throws AuthException, IOException, ResourceConflictException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    IdentifiedUser me = currentUser.get().asIdentifiedUser();
    String refPrefix = RefNames.refsEditPrefix(me.getAccountId(), change.getId());

    try (Repository repo = gitManager.openRepository(change.getProject())) {
      Map<String, Ref> refs = repo.getRefDatabase().getRefs(refPrefix);
      if (!refs.isEmpty()) {
        throw new ResourceConflictException("edit already exists");
      }

      try (RevWalk rw = new RevWalk(repo)) {
        ObjectId revision = ObjectId.fromString(ps.getRevision().get());
        String editRefName = RefNames.refsEdit(me.getAccountId(), change.getId(),
            base.getId());
        Result res =
            update(repo, me, editRefName, rw, ObjectId.zeroId(), revision);
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
      ObjectId commit = createCommit(me, inserter, prevEdit,
          prevEdit.getTree(),
          msg);
      inserter.flush();
      return update(repo, me, refName, rw, prevEdit, commit);
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
    return modify(TreeOperation.CHANGE_ENTRY, edit, file, null, content);
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
    return modify(TreeOperation.DELETE_ENTRY, edit, file, null, null);
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
    return modify(TreeOperation.RENAME_ENTRY, edit, file, newFile, null);
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
    return modify(TreeOperation.RESTORE_ENTRY, edit, file, null, null);
  }

  private RefUpdate.Result modify(TreeOperation op, ChangeEdit edit,
      String file, @Nullable String newFile, @Nullable RawInput content)
      throws AuthException, IOException, InvalidChangeOperationException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    IdentifiedUser me = currentUser.get().asIdentifiedUser();
    Project.NameKey project = edit.getChange().getProject();
    try (Repository repo = gitManager.openRepository(project);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter inserter = repo.newObjectInserter();
        ObjectReader reader = repo.newObjectReader()) {
      String refName = edit.getRefName();
      RevCommit prevEdit = edit.getEditCommit();
      ObjectId newTree = writeNewTree(op,
          rw,
          inserter,
          prevEdit,
          reader,
          file,
          newFile,
          toBlob(inserter, content));
      if (ObjectId.equals(newTree, prevEdit.getTree())) {
        throw new InvalidChangeOperationException("no changes were made");
      }

      ObjectId commit = createCommit(me, inserter, prevEdit, newTree);
      inserter.flush();
      return update(repo, me, refName, rw, prevEdit, commit);
    }
  }

  private static ObjectId toBlob(ObjectInserter ins, @Nullable RawInput content)
      throws IOException {
    if (content == null) {
      return null;
    }

    long len = content.getContentLength();
    InputStream in = content.getInputStream();
    if (len < 0) {
      return ins.insert(OBJ_BLOB, ByteStreams.toByteArray(in));
    }
    return ins.insert(OBJ_BLOB, len, in);
  }

  private ObjectId createCommit(IdentifiedUser me, ObjectInserter inserter,
      RevCommit revision, ObjectId tree) throws IOException {
    return createCommit(me, inserter, revision, tree,
        revision.getFullMessage());
  }

  private ObjectId createCommit(IdentifiedUser me, ObjectInserter inserter,
      RevCommit revision, ObjectId tree, String msg)
      throws IOException {
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(tree);
    builder.setParentIds(revision.getParents());
    builder.setAuthor(revision.getAuthorIdent());
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
    ru.setRefLogMessage("inline edit (amend)", false);
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
      String fileName, @Nullable String newFile,
      @Nullable final ObjectId content) throws IOException {
    DirCache newTree = readTree(reader, prevEdit);
    DirCacheEditor dce = newTree.editor();
    switch (op) {
      case DELETE_ENTRY:
        dce.add(new DeletePath(fileName));
        break;

      case RENAME_ENTRY:
        rw.parseHeaders(prevEdit);
        TreeWalk tw =
            TreeWalk.forPath(rw.getObjectReader(), fileName, prevEdit.getTree());
        if (tw != null) {
          dce.add(new DeletePath(fileName));
          addFileToCommit(newFile, dce, tw);
        }
        break;

      case CHANGE_ENTRY:
        checkNotNull(content, "new content required");
        dce.add(new PathEdit(fileName) {
          @Override
          public void apply(DirCacheEntry ent) {
            if (ent.getRawMode() == 0) {
              ent.setFileMode(FileMode.REGULAR_FILE);
            }
            ent.setObjectId(content);
          }
        });
        break;

      case RESTORE_ENTRY:
        if (prevEdit.getParentCount() == 0) {
          dce.add(new DeletePath(fileName));
          break;
        }

        RevCommit base = prevEdit.getParent(0);
        rw.parseHeaders(base);
        tw = TreeWalk.forPath(rw.getObjectReader(), fileName, base.getTree());
        if (tw == null) {
          dce.add(new DeletePath(fileName));
          break;
        }

        addFileToCommit(fileName, dce, tw);
        break;
    }
    dce.finish();
    return newTree.writeTree(ins);
  }

  private static void addFileToCommit(String newFile, DirCacheEditor dce,
      TreeWalk tw) {
    final FileMode mode = tw.getFileMode(0);
    final ObjectId oid = tw.getObjectId(0);
    dce.add(new PathEdit(newFile) {
      @Override
      public void apply(DirCacheEntry ent) {
        ent.setFileMode(mode);
        ent.setObjectId(oid);
      }
    });
  }

  private static DirCache readTree(ObjectReader reader, RevCommit prevEdit)
      throws IOException {
    DirCache dc = DirCache.newInCore();
    DirCacheBuilder b = dc.builder();
    b.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, prevEdit.getTree());
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
