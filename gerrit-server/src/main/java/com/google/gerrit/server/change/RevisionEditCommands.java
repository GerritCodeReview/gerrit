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

import com.google.common.base.Charsets;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
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
import org.eclipse.jgit.lib.PersonIdent;
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
import java.util.Set;

/* Commands to manipulate user's edits on top of patch sets */
public class RevisionEditCommands {

  enum TreeOperation {
    CHANGE_ENTRY,
    DELETE_ENTRY
  }

  private final PersonIdent myIdent;
  private final GitRepositoryManager gitManager;
  private final Provider<ReviewDb> dbProvider;
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
    this.dbProvider = dbProvider;
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
      RevCommit commit = getCommit(repo, edit);
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
      ru.addCommand(new ReceiveCommand(ObjectId.zeroId(), commit,
          ps.getId().toRefName()));
      RevWalk rw = new RevWalk(repo);

      ChangeControl ctl = changeControlFactory.controlFor(c, me);
      PatchSetInserter inserter =
          patchSetInserterFactory.create(repo, rw, ctl.getRefControl(), me, c,
              commit);
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
        if (cmd.getResult() != ReceiveCommand.Result.OK
            // TODO(davido): why LOCK_FAILURE is a valid return code?
            && cmd.getResult() != ReceiveCommand.Result.LOCK_FAILURE) {
          throw new IOException("failed to update: " + cmd);
        }
      }
    } finally {
      repo.close();
    }
  }

  public void delete(Change change, PatchSet ps) throws AuthException,
      NoSuchChangeException, IOException, OrmException {
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
      RevCommit commit = getCommit(repo, edit);
      if (commit == null) {
        throw new NoSuchChangeException(change.getId());
      }
      BatchRefUpdate ru = repo.getRefDatabase().newBatchUpdate();
      ru.addCommand(new ReceiveCommand(commit,
          ObjectId.zeroId(), edit.toRefName()));
      RevWalk rw = new RevWalk(repo);
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
      refreshChange(change.getId());
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
      Set<String> names =
          repo.getRefDatabase()
              .getRefs(RevisionEdit.refPrefix(me, change.getId()).toString())
              .keySet();
      Map<PatchSet.Id, PatchSet> result = new HashMap<>(names.size());
      for (String name : names) {
        PatchSet.Id psid = new PatchSet.Id(change.getId(),
            Integer.valueOf(name), true);
        RevisionEdit edit = new RevisionEdit(me, PatchSet.Id.editFrom(psid));
        result.put(psid, edit.getPatchSet(repo));
      }
      return Collections.unmodifiableMap(result);
    } finally {
      repo.close();
    }
  }

  public RefUpdate.Result deleteContent(Change change, PatchSet ps, String file)
      throws AuthException, InvalidChangeOperationException, NoSuchChangeException,
      IOException, OrmException {
    op = TreeOperation.DELETE_ENTRY;
    return modify(change, ps, file, null);
  }

  public RefUpdate.Result edit(Change change, PatchSet ps, String file,
      String content) throws AuthException, InvalidChangeOperationException,
      NoSuchChangeException, IOException, OrmException {
    op = TreeOperation.CHANGE_ENTRY;
    return modify(change, ps, file, content);
  }

  private RefUpdate.Result modify(Change change, PatchSet ps, String file,
      String content) throws AuthException, IOException, NoSuchChangeException,
      MissingObjectException, IncorrectObjectTypeException,
      InvalidChangeOperationException, OrmException {
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
      try {
        RevCommit prevEdit = edit.get(git, rw);
        RevCommit base = rw.parseCommit(ObjectId.fromString(
            ps.getRevision().get()));
        ObjectId oldObjectId = prevEdit;
        if (prevEdit == null) {
          prevEdit = base;
          oldObjectId = ObjectId.zeroId();
        }

        ObjectId tree = editTree(file, git, inserter, content, prevEdit);
        if (ObjectId.equals(tree, prevEdit.getTree())) {
          throw new InvalidChangeOperationException("no changes were made");
        }

        RefUpdate.Result res = commitTree(git, me, edit, rw,
            inserter, prevEdit, base, tree, oldObjectId);
        refreshChange(change.getId());
        return res;
      } finally {
        rw.release();
        inserter.release();
      }
    } finally {
      git.close();
    }
  }

  private RefUpdate.Result commitTree(final Repository git,
      IdentifiedUser me, RevisionEdit edit, RevWalk rw,
      ObjectInserter inserter, RevCommit prevEdit, RevCommit base,
      ObjectId tree, ObjectId oldObjectId)
      throws IOException {
    ObjectId newEdit = commit(me, inserter, prevEdit, base, tree);
    return update(git, me, edit, rw, prevEdit, base, newEdit, oldObjectId);
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
      ObjectId newEdit, ObjectId oldObjectId) throws IOException {
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

  private ObjectId editTree(String fileName, Repository repo,
      ObjectInserter ins, String content, RevCommit prevEdit)
      throws IOException {
    // TODO(dborowitz): Handle non-UTF-8 paths.
    byte[] path = fileName.getBytes(Charsets.UTF_8);
    DirCache dc = DirCache.newInCore();
    DirCacheBuilder dcb = dc.builder();
    TreeWalk tw = new TreeWalk(repo);
    tw.reset();
    tw.setRecursive(true);
    tw.addTree(prevEdit.getTree());
    // TODO(dborowitz): This is awful. Surely we can do this without
    // iterating the entire tree. Take a closer look at ResolveMerger to
    // figure this out.
    boolean found = false;
    while (tw.next()) {
      DirCacheEntry dce = new DirCacheEntry(tw.getRawPath());
      dce.setFileMode(tw.getFileMode(0));
      if (tw.isPathPrefix(path, path.length) == 0
          && tw.getPathLength() == path.length) {
        if (tw.isSubtree()) {
          throw new IllegalArgumentException("invalid PUT on directory");
        }
        found = true;
        if (op == TreeOperation.CHANGE_ENTRY) {
          dce.setObjectId(insert(ins, content));
        } else {
          // skip that file from the tree: we are deleting
          continue;
        }
      } else {
        dce.setObjectId(tw.getObjectId(0));
      }
      dcb.add(dce);
    }
    if (!found) {
      if (op == TreeOperation.DELETE_ENTRY) {
        throw new IllegalStateException("Cannot remove entry from tree");
      }
      // TODO(dborowitz): Use path compare above to insert in order.
      DirCacheEntry dce = new DirCacheEntry(path);
      dce.setFileMode(FileMode.REGULAR_FILE);
      dce.setObjectId(insert(ins, content));
      dcb.add(dce);
    }
    dcb.finish();
    return dc.writeTree(ins);
  }

  private ObjectId insert(ObjectInserter ins, String content)
      throws IOException {
    return ins.insert(Constants.OBJ_BLOB, Constants.encode(content));
  }

  private PersonIdent getCommitterIdent(IdentifiedUser user) {
    return user.newCommitterIdent(myIdent.getWhen(), myIdent.getTimeZone());
  }

  private PersonIdent getRefLogIdent(IdentifiedUser user) {
    return user.newRefLogIdent(myIdent.getWhen(), myIdent.getTimeZone());
  }

  private RevCommit getCommit(Repository repo, RevisionEdit edit)
      throws IOException {
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        return edit.get(repo, rw);
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  // TODO(davido): can we refresh cache without updating change record?
  // Another approach would be to upgarde the database and introduce a
  // new column that is only get upgraded when CRUD operation on revision
  // was executed. Obviously new field would contribute to hash building
  // for ETag cache tag
  private void refreshChange(Change.Id id) throws OrmException {
    ReviewDb db = dbProvider.get();
    db.changes().beginTransaction(id);
    try {
      db.changes().atomicUpdate(
        id,
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            ChangeUtil.updated(change);
            return change;
          }
        });
      db.commit();
    } finally {
      db.rollback();
    }
  }
}
