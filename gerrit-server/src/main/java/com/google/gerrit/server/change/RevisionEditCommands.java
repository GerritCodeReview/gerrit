// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/* Commands to manipulate user's edits on top of patch sets */
public class RevisionEditCommands {

  enum TreeOperation {
    CHANGE_ENTRY,
    DELETE_ENTRY,
    RESTORE_ENTRY
  }

  private final PersonIdent myIdent;
  private final GitRepositoryManager gitManager;
  private final Provider<CurrentUser> currentUser;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private TreeOperation op;

  @Inject
  RevisionEditCommands(@GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager gitManager,
      Provider<ReviewDb> dbProvider,
      Provider<CurrentUser> currentUser,
      PatchSetInserter.Factory patchSetInserterFactory,
      ChangeControl.GenericFactory changeControlFactory) {
    this.myIdent = myIdent;
    this.gitManager = gitManager;
    this.currentUser = currentUser;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.changeControlFactory = changeControlFactory;
  }

  public void publish(Change c, PatchSet basePs)
      throws AuthException, NoSuchChangeException, IOException,
      InvalidChangeOperationException, OrmException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("edits only available to authenticated users");
    }

    IdentifiedUser me = (IdentifiedUser) currentUser.get();
    Project.NameKey project = c.getProject();
    RevisionEdit edit = new RevisionEdit(me,
        PatchSet.Id.editFrom(basePs.getId()));

    Repository repo = gitManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(repo);
      RevCommit commit = edit.get(repo, rw);
      if (commit == null) {
        throw new NoSuchChangeException(c.getId());
      }

      PatchSet ps = new PatchSet(new PatchSet.Id(c.getId(),
          c.currentPatchSetId().get() + 1));
      ps.setRevision(new RevId(ObjectId.toString(commit)));
      ps.setUploader(me.getAccountId());
      ps.setCreatedOn(new Timestamp(System.currentTimeMillis()));

      BatchRefUpdate ru = repo.getRefDatabase().newBatchUpdate();
      ru.addCommand(new ReceiveCommand(commit, ObjectId.zeroId(),
          edit.toRefName()));

      PatchSetInserter inserter =
          patchSetInserterFactory.create(repo, rw,
              changeControlFactory.controlFor(c, me), commit);
      inserter.setPatchSet(ps).setMessage(String
          .format("Patch Set %d: New edit was published",
              basePs.getPatchSetId()))
          .insert();
      try {
        ru.execute(rw, NullProgressMonitor.INSTANCE);
      } finally {
        rw.release();
      }
      for (ReceiveCommand cmd : ru.getCommands()) {
        if (cmd.getResult() != ReceiveCommand.Result.OK) {
          throw new IOException("failed to update: " + cmd);
        }
      }
    } finally {
      repo.close();
    }
  }

  public void delete(Change change, PatchSet ps) throws AuthException,
      NoSuchChangeException, IOException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("edits only available to authenticated users");
    }

    final Repository repo;
    try {
      repo = gitManager.openRepository(change.getProject());
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(change.getId(), e);
    }

    IdentifiedUser me = (IdentifiedUser) currentUser.get();
    RevisionEdit edit = new RevisionEdit(me, PatchSet.Id.editFrom(ps.getId()));
    try {
      RevWalk rw = new RevWalk(repo);
      RevCommit commit = edit.get(repo, rw);
      if (commit == null) {
        throw new NoSuchChangeException(change.getId());
      }
      BatchRefUpdate ru = repo.getRefDatabase().newBatchUpdate();
      ru.addCommand(new ReceiveCommand(commit,
          ObjectId.zeroId(), edit.toRefName()));
      try {
        ru.execute(rw, NullProgressMonitor.INSTANCE);
      } finally {
        rw.release();
      }
      for (ReceiveCommand cmd : ru.getCommands()) {
        if (cmd.getResult() != ReceiveCommand.Result.OK) {
          throw new IOException("failed to delete: " + cmd);
        }
      }
    } finally {
      repo.close();
    }
  }

  public Map<PatchSet.Id, PatchSet> read(Change change)
      throws AuthException, InvalidChangeOperationException,
      NoSuchChangeException, IOException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("edits only available to authenticated users");
    }
    final Repository repo;
    try {
      repo = gitManager.openRepository(change.getProject());
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(change.getId(), e);
    }

    try {
      IdentifiedUser me = (IdentifiedUser) currentUser.get();
      Map<String, Ref> names = repo.getRefDatabase()
          .getRefs(RevisionEdit.refPrefix(me, change.getId()).toString());
      Map<PatchSet.Id, PatchSet> result = new HashMap<>(names.size());
      for (Map.Entry<String, Ref> entry : names.entrySet()) {
        PatchSet.Id psid = new PatchSet.Id(change.getId(),
            Integer.valueOf(entry.getKey()), true);
        RevisionEdit edit = new RevisionEdit(me, psid, entry.getValue());
        result.put(psid, edit.getPatchSet(repo));
      }
      return Collections.unmodifiableMap(result);
    } finally {
      repo.close();
    }
  }

  public RefUpdate.Result deleteContent(Change change, PatchSet ps, String file)
      throws AuthException, InvalidChangeOperationException, NoSuchChangeException,
      IOException {
    op = TreeOperation.DELETE_ENTRY;
    return modify(change, ps, file, null);
  }

  public RefUpdate.Result edit(Change change, PatchSet ps, String file,
      byte[] content)
      throws AuthException, InvalidChangeOperationException,
      NoSuchChangeException, IOException {
    op = TreeOperation.CHANGE_ENTRY;
    return modify(change, ps, file, content);
  }

  public RefUpdate.Result restore(Change change, PatchSet ps, String file)
      throws AuthException, InvalidChangeOperationException,
      NoSuchChangeException, IOException {
    op = TreeOperation.RESTORE_ENTRY;
    return modify(change, ps, file, null);
  }

  private RefUpdate.Result modify(Change change, PatchSet ps, String file,
      byte[] content) throws AuthException, IOException, NoSuchChangeException,
      MissingObjectException, IncorrectObjectTypeException,
      InvalidChangeOperationException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("edits only available to authenticated users");
    }

    final Repository git;
    try {
      git = gitManager.openRepository(change.getProject());
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(change.getId(), e);
    }

    IdentifiedUser me = (IdentifiedUser)currentUser.get();
    RevisionEdit edit = new RevisionEdit(me, PatchSet.Id.editFrom(ps.getId()));
    try {
      RevWalk rw = new RevWalk(git);
      ObjectInserter inserter = git.newObjectInserter();
      ObjectReader reader = git.newObjectReader();
      try {
        RevCommit prevEdit = edit.get(git, rw);
        RevCommit base = rw.parseCommit(ObjectId.fromString(
            ps.getRevision().get()));
        ObjectId oldObjectId = prevEdit;
        if (prevEdit == null) {
          prevEdit = base;
          oldObjectId = ObjectId.zeroId();
        }
        ObjectId newTree = writeNewTree(file, git, rw, inserter,
            reader, content, base, prevEdit);
        if (ObjectId.equals(newTree, prevEdit.getTree())) {
          throw new InvalidChangeOperationException("no changes were made");
        }

        return update(git, me, edit, rw, prevEdit, base, oldObjectId,
            commit(me, inserter, prevEdit, base, newTree));
      } finally {
        rw.release();
        inserter.release();
        reader.release();
      }
    } finally {
      git.close();
    }
  }

  private ObjectId commit(IdentifiedUser me, ObjectInserter inserter,
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

  private RefUpdate.Result update(final Repository git, IdentifiedUser me,
      RevisionEdit edit, RevWalk rw, RevCommit prevEdit, RevCommit base,
      ObjectId oldObjectId, ObjectId newEdit) throws IOException {
    RefUpdate ru = git.updateRef(edit.toRefName());
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

  private ObjectId writeNewTree(String fileName, Repository repo,
      RevWalk rw, ObjectInserter ins, ObjectReader reader, byte[] content,
      RevCommit base, RevCommit prevEdit)
      throws IOException, InvalidChangeOperationException {
    DirCache newTree = newTree(reader, prevEdit);
    editTree(
        repo,
        base,
        newTree.editor(),
        ins,
        fileName,
        content);
    return newTree.writeTree(ins);
  }

  private void editTree(Repository repo, RevCommit base, DirCacheEditor e,
      ObjectInserter i, String f, byte[] c)
      throws IOException, InvalidChangeOperationException {
    switch (op) {
      case CHANGE_ENTRY:
      case RESTORE_ENTRY:
        e.add(getPathEdit(repo, base, f, i, c));
        break;
      case DELETE_ENTRY:
        e.add(new DeletePath(f));
        break;
      default:
        throw new IllegalStateException("unknown tree operation");
    }
    e.finish();
  }

  private PathEdit getPathEdit(Repository repo, RevCommit base, String f,
      ObjectInserter i, byte[] c)
      throws IOException, InvalidChangeOperationException {
    final ObjectId oid = op == TreeOperation.CHANGE_ENTRY
        ? i.insert(Constants.OBJ_BLOB, c)
        : getObjectIdForRestoreOperation(repo, base, f);
    return new PathEdit(f) {
      @Override
      public void apply(DirCacheEntry ent) {
        ent.setFileMode(FileMode.REGULAR_FILE);
        ent.setObjectId(oid);
      }
    };
  }

  private ObjectId getObjectIdForRestoreOperation(Repository repo,
      RevCommit base, String f)
      throws IOException, InvalidChangeOperationException {
    RevWalk rw = new RevWalk(repo);
    try {
      TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), f,
          base.getTree().getId());
      if (tw == null) {
        tw = TreeWalk.forPath(rw.getObjectReader(), f,
            rw.parseCommit(base.getParent(0)).getTree().getId());
      }
      if (tw == null) {
        throw new InvalidChangeOperationException(
            String.format("can not restore path: %s", f));
      }
      return tw.getObjectId(0);
    } finally {
      rw.release();
    }
  }

  private static DirCache newTree(ObjectReader reader, RevCommit prevEdit)
      throws IOException {
    DirCache dc = DirCache.newInCore();
    DirCacheBuilder b = dc.builder();
    b.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, prevEdit.getTree()
        .getId());
    b.finish();
    return dc;
  }

  private PersonIdent getCommitterIdent(IdentifiedUser user) {
    return user.newCommitterIdent(myIdent.getWhen(), myIdent.getTimeZone());
  }

  private PersonIdent getRefLogIdent(IdentifiedUser user) {
    return user.newRefLogIdent(myIdent.getWhen(), myIdent.getTimeZone());
  }
}
