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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;

/* Revision edit modifier command */
public class RevisionEditModifier {

  enum TreeOperation {
    CHANGE_ENTRY,
    DELETE_ENTRY,
    RESTORE_ENTRY
  }

  private final PersonIdent myIdent;
  private final GitRepositoryManager gitManager;
  private final Provider<CurrentUser> currentUser;
  private TreeOperation op;

  @Inject
  RevisionEditModifier(@GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager gitManager,
      Provider<ReviewDb> dbProvider,
      Provider<CurrentUser> currentUser) {
    this.myIdent = myIdent;
    this.gitManager = gitManager;
    this.currentUser = currentUser;
  }

  public RefUpdate.Result editContent(Change change, PatchSet ps, String file,
      byte[] content) throws AuthException, InvalidChangeOperationException,
      IOException {
    op = TreeOperation.CHANGE_ENTRY;
    return modify(change, ps, file, content);
  }

  public RefUpdate.Result deleteContent(Change change, PatchSet ps, String file)
      throws AuthException, InvalidChangeOperationException, IOException {
    op = TreeOperation.DELETE_ENTRY;
    return modify(change, ps, file, null);
  }

  public RefUpdate.Result restoreContent(Change change, PatchSet ps, String file)
      throws AuthException, InvalidChangeOperationException, IOException {
    op = TreeOperation.RESTORE_ENTRY;
    return modify(change, ps, file, null);
  }

  private RefUpdate.Result modify(Change change, PatchSet ps, String file,
      byte[] content) throws AuthException, IOException,
      InvalidChangeOperationException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    Repository repo = gitManager.openRepository(change.getProject());

    IdentifiedUser me = (IdentifiedUser)currentUser.get();
    RevisionEdit edit = new RevisionEdit(me, PatchSet.Id.editFrom(ps.getId()));
    try {
      RevWalk rw = new RevWalk(repo);
      ObjectInserter inserter = repo.newObjectInserter();
      ObjectReader reader = repo.newObjectReader();
      try {
        RevCommit prevEdit = edit.getCommit(repo, rw);
        RevCommit base = rw.parseCommit(ObjectId.fromString(
            ps.getRevision().get()));
        ObjectId oldObjectId = prevEdit;
        if (prevEdit == null) {
          prevEdit = base;
          oldObjectId = ObjectId.zeroId();
        }
        ObjectId newTree = writeNewTree(repo, rw, inserter,
            prevEdit, reader, file, content, base);
        if (ObjectId.equals(newTree, prevEdit.getTree())) {
          throw new InvalidChangeOperationException("no changes were made");
        }

        return update(repo, me, edit, rw, prevEdit, base, oldObjectId,
            createCommit(me, inserter, prevEdit, base, newTree));
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
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(tree);
    builder.setParentIds(base.getParents());
    builder.setAuthor(prevEdit.getAuthorIdent());
    builder.setCommitter(getCommitterIdent(me));
    builder.setMessage(prevEdit.getFullMessage());
    ObjectId newEdit = inserter.insert(builder);
    inserter.flush();
    return newEdit;
  }

  private RefUpdate.Result update(Repository repo, IdentifiedUser me,
      RevisionEdit edit, RevWalk rw, RevCommit prevEdit, RevCommit base,
      ObjectId oldObjectId, ObjectId newEdit) throws IOException {
    RefUpdate ru = repo.updateRef(edit.getRefName());
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

  private ObjectId writeNewTree(Repository repo, RevWalk rw, ObjectInserter ins,
      RevCommit prevEdit, ObjectReader reader, String fileName, byte[] content,
      RevCommit base)
      throws IOException, InvalidChangeOperationException {
    DirCache newTree = createTree(reader, prevEdit);
    editTree(
        repo,
        base,
        newTree.editor(),
        ins,
        fileName,
        content);
    return newTree.writeTree(ins);
  }

  private void editTree(Repository repo, RevCommit base, DirCacheEditor dce,
      ObjectInserter ins, String path, byte[] content)
      throws IOException, InvalidChangeOperationException {
    switch (op) {
      case CHANGE_ENTRY:
      case RESTORE_ENTRY:
        dce.add(getPathEdit(repo, base, path, ins, content));
        break;
      case DELETE_ENTRY:
        dce.add(new DeletePath(path));
        break;
      default:
        throw new IllegalStateException("unknown tree operation");
    }
    dce.finish();
  }

  private PathEdit getPathEdit(Repository repo, RevCommit base, String path,
      ObjectInserter ins, byte[] content)
      throws IOException, InvalidChangeOperationException {
    final ObjectId oid = op == TreeOperation.CHANGE_ENTRY
        ? ins.insert(Constants.OBJ_BLOB, content)
        : getObjectIdForRestoreOperation(repo, base, path);
    return new PathEdit(path) {
      @Override
      public void apply(DirCacheEntry ent) {
        ent.setFileMode(FileMode.REGULAR_FILE);
        ent.setObjectId(oid);
      }
    };
  }

  private PersonIdent getCommitterIdent(IdentifiedUser user) {
    return user.newCommitterIdent(myIdent.getWhen(), myIdent.getTimeZone());
  }

  private PersonIdent getRefLogIdent(IdentifiedUser user) {
    return user.newRefLogIdent(myIdent.getWhen(), myIdent.getTimeZone());
  }

  private static ObjectId getObjectIdForRestoreOperation(Repository repo,
      RevCommit base, String path)
      throws IOException, InvalidChangeOperationException {
    RevWalk rw = new RevWalk(repo);
    try {
      TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), path,
          base.getTree().getId());
      // If the file does not exist in the edit, still allow it to be
      // restored from the base of the edit.
      if (tw == null) {
        tw = TreeWalk.forPath(rw.getObjectReader(), path,
            rw.parseCommit(base.getParent(0)).getTree().getId());
      }
      if (tw == null) {
        throw new InvalidChangeOperationException(
            String.format("can not restore path: %s", path));
      }
      return tw.getObjectId(0);
    } finally {
      rw.release();
    }
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
}
