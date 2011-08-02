// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.AtomicEntry;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Subscription;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HandleAtomicCommits {

  public interface Factory {
    HandleAtomicCommits create(Repository repo, GitRepositoryManager repoManager);
  }

  private ReviewDb db;
  private final Repository repo;
  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ReplicationQueue replication;

  private final List<AtomicEntry> newAtomicEntries;
  private final List<ObjectId> gitLinksAlreadyUploaded;

  @Inject
  public HandleAtomicCommits(final SchemaFactory<ReviewDb> sf,
      final ReplicationQueue replication, @Assisted Repository repo,
      @Assisted GitRepositoryManager repoManager) {

    this.schemaFactory = sf;
    this.repo = repo;
    this.repoManager = repoManager;
    this.replication = replication;
    newAtomicEntries = new ArrayList<AtomicEntry>();
    gitLinksAlreadyUploaded = new ArrayList<ObjectId>();
  }

  /**
   * It handles atomic entries to be inserted, updated or deleted to a received
   * commit when a change is replaced.
   * <p>
   * If no atomic entry should be inserted/updated/deleted, nothing is performed
   * by the method.
   * <p>
   * A commit of a super project containing git links should insert atomic entry
   * for each git link referring to a project already registered as a
   * subscription and pointing to a commit sha-1 not already integrated in the
   * "sub-project". Each inserted atomic entry will have no source change id
   * value.
   * <p>
   * It is also considered that a received commit could be one of a change to
   * fill an atomic entry sourceChangeId field.
   * <p>
   * In case a source change involved in an atomic entry gets a new patch, it
   * automatically updates the super change it is related to.
   *
   * @param revCommit The received commit.
   * @param change The change being replaced.
   * @throws OrmException Data removal failure.
   * @throws MissingObjectException Tree object not found.
   * @throws IncorrectObjectTypeException An inconsistency with respect to
   *         handling different object types.
   * @throws CorruptObjectException An object could not be read from Git.
   * @throws IOException A Git repository could not be found.
   */
  public void handleAtReplaceChange(RevCommit revCommit, Change change)
      throws OrmException, MissingObjectException,
      IncorrectObjectTypeException, CorruptObjectException, IOException {
    if (db == null) {
      db = schemaFactory.open();
    }

    List<AtomicEntry> existingAtomicEntries =
        db.atomicEntries().bySuperChangeId(change.getId()).toList();

    addNewEntries(revCommit, change);
    fillSourceChangeId(revCommit, change, true);

    // Update db with the difference between existing and the new atomicEntries

    final Map<String, AtomicEntry> toBeDeleted =
        new HashMap<String, AtomicEntry>();
    for (AtomicEntry entry : existingAtomicEntries) {
      toBeDeleted.put(entry.getId().get(), entry);
    }

    List<AtomicEntry> toBeInserted = new ArrayList<AtomicEntry>();
    toBeInserted.addAll(newAtomicEntries);

    for (AtomicEntry entry : newAtomicEntries) {
      if (existingAtomicEntries.contains(entry)) {
        toBeDeleted.remove(entry.getId().get());
        toBeInserted.remove(entry);
      }
    }

    for (ObjectId objectId : gitLinksAlreadyUploaded) {
      toBeDeleted.remove(objectId.getName());
    }

    final List<AtomicEntry> listToBeDeleted =
        new ArrayList<AtomicEntry>(toBeDeleted.values());
    db.atomicEntries().delete(listToBeDeleted);
    db.atomicEntries().insert(toBeInserted);
    db.close();
  }

  /**
   * It handles atomic entries to be inserted to a received commit when a change
   * is created.
   * <p>
   * If no atomic entry should be inserted, nothing is performed by the method.
   * <p>
   * A commit of a super project containing git links should insert atomic entry
   * for each git link referring to a project already registered as a
   * subscription and pointing to a commit sha-1 not already integrated in the
   * "sub-project". Each inserted atomic entry will have no source change id
   * value.
   * <p>
   * It is also considered that a received commit could be one of a change to
   * fill an atomic entry sourceChangeId field.
   *
   * @param revCommit The received commit.
   * @param change The change associated with the commit.
   * @throws OrmException Data removal failure.
   * @throws MissingObjectException Tree object not found.
   * @throws IncorrectObjectTypeException An inconsistency with respect to
   *         handling different object types.
   * @throws CorruptObjectException An object could not be read from Git.
   * @throws IOException A Git repository could not be found.
   */
  public void handleAtCreateChange(final RevCommit revCommit,
      final Change change) throws OrmException, MissingObjectException,
      IncorrectObjectTypeException, CorruptObjectException, IOException {

    if (db == null) {
      db = schemaFactory.open();
    }

    addNewEntries(revCommit, change);
    fillSourceChangeId(revCommit, change, false);

    db.atomicEntries().insert(newAtomicEntries);

    db.close();
  }

  private void fillSourceChangeId(final RevCommit revCommit,
      final Change change, boolean handleAtReplace) throws OrmException,
      UnmergedPathException, IOException {
    final String commitSha1 = revCommit.getId().getName();

    final AtomicEntry aeToSourceSha1 =
        db.atomicEntries().bySourceSha1(commitSha1).iterator().hasNext() ? db
            .atomicEntries().bySourceSha1(commitSha1).iterator().next() : null;

    if (aeToSourceSha1 != null) {
      // There was an entry waiting this new change.
      // This new change is a source one.
      aeToSourceSha1.setSourceChangeId(change.getId());
      db.atomicEntries().update(Collections.singleton(aeToSourceSha1));
    } else if (handleAtReplace) {
      updateSuperChange(revCommit, change);
    }
  }

  private void updateSuperChange(final RevCommit revCommit, final Change change)
      throws OrmException, UnmergedPathException, IOException {
    final List<AtomicEntry> sourceEntries =
        db.atomicEntries().bySourceChangeId(change.getId()).toList();

    for (AtomicEntry ae : sourceEntries) {
      final Change superChange = db.changes().get(ae.getId().getParentKey());

      db.atomicEntries().delete(Collections.singleton(ae));
      ae.setId(new AtomicEntry.Id(ae.getId().getParentKey(), revCommit.getId()
          .getName()));
      db.atomicEntries().insert(Collections.singleton(ae));

      final String sourcePath =
          db.subscriptions().get(
              new Subscription.Key(superChange.getDest(), change.getDest()))
              .getPath();

      updateSuperChange(revCommit, sourcePath, superChange);
    }
  }

  private void addNewEntries(final RevCommit revCommit, final Change change)
      throws MissingObjectException, IncorrectObjectTypeException,
      CorruptObjectException, IOException, OrmException {
    // Let's check if this change would be a super project one
    // having git links in the commit.

    // Scanning the changed files in this commit

    gitLinksAlreadyUploaded.clear();

    final TreeWalk tw = new TreeWalk(repo);
    if (revCommit.getParentCount() == 0) {
      tw.addTree(emptyTree(repo));
    } else {
      tw.addTree(revCommit.getParent(0).getTree());
    }
    tw.addTree(revCommit.getTree());
    final List<DiffEntry> diffEntries = DiffEntry.scan(tw);

    for (DiffEntry de : diffEntries) {
      final TreeWalk diffEntryFile =
          TreeWalk.forPath(repo, de.getNewPath(), revCommit.getTree());

      if (diffEntryFile.getFileMode(0) == FileMode.GITLINK) {
        Iterator<Subscription> subscriptionIterator =
            db.subscriptions().getSubscription(change.getDest(),
                de.getNewPath()).iterator();
        if (subscriptionIterator.hasNext()) {
          Project.NameKey submoduleRepoName =
              subscriptionIterator.next().getSource().getParentKey();
          Repository submoduleRepo =
              repoManager.openRepository(submoduleRepoName);
          if (!submoduleRepo.hasObject(diffEntryFile.getObjectId(0))) {
            // A modified file is a git link:
            // it should then create a new AtomicEntry
            // without the sourceChangeId value, to be filled
            // only when the sourceChangeId is created.
            addNewEntry(change.getId(), diffEntryFile.getObjectId(0));
          } else {
            gitLinksAlreadyUploaded.add(diffEntryFile.getObjectId(0));
          }
        }
      }
    }
  }

  private void addNewEntry(final Change.Id superProjectChangeId,
      final ObjectId sourceSha1) throws OrmException {
    final AtomicEntry newEntry =
        new AtomicEntry(new AtomicEntry.Id(superProjectChangeId, sourceSha1
            .getName()));
    newAtomicEntries.add(newEntry);
  }

  private static DirCache getGitDirCache(Repository pdb, Ref branch)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    RevWalk rw = new RevWalk(pdb);

    DirCache dc = DirCache.newInCore();
    DirCacheBuilder b = dc.builder();

    b.addTree(new byte[0], // no prefix path
        DirCacheEntry.STAGE_0, // standard stage
        pdb.newObjectReader(), rw.parseTree(branch.getObjectId()));
    b.finish();
    return dc;
  }

  private void updateSuperChange(final RevCommit sourceCommit,
      final String sourcePath, final Change superChange) throws OrmException,
      UnmergedPathException, IOException {
    final Project.NameKey superProjectNameKey =
        superChange.getDest().getParentKey();
    final Project.NameKey superProjectName =
        superChange.getDest().getParentKey();
    final Repository superProjectRepo =
        repoManager.openRepository(superProjectName);

    final List<PatchSet> allSuperChangePatches =
        db.patchSets().byChange(superChange.getId()).toList();
    final PatchSet superPatchToUpdate =
        allSuperChangePatches.get(allSuperChangePatches.size() - 1);

    final String refNameToUpdate = superPatchToUpdate.getRefName();

    final DirCache dc =
        getGitDirCache(superProjectRepo, superProjectRepo
            .getRef(refNameToUpdate));
    final ObjectId sourceCommitSha1 = sourceCommit.getId().copy();

    DirCacheEditor ed = dc.editor();
    ed.add(new PathEdit(sourcePath) {
      public void apply(DirCacheEntry ent) {
        ent.setFileMode(FileMode.GITLINK);
        ent.setObjectId(sourceCommitSha1);
      }
    });
    ed.finish();

    final ObjectInserter oi = superProjectRepo.newObjectInserter();
    final ObjectId tree = dc.writeTree(oi);

    final RefUpdate rfu = superProjectRepo.updateRef(refNameToUpdate);

    final CommitBuilder commit = new CommitBuilder();

    final RevWalk revWalk = new RevWalk(superProjectRepo);
    final RevCommit oldSuperProjectCommit =
        revWalk.parseCommit(rfu.getOldObjectId());

    commit.setAuthor(oldSuperProjectCommit.getAuthorIdent());
    commit.setCommitter(oldSuperProjectCommit.getCommitterIdent());
    commit.setMessage(oldSuperProjectCommit.getFullMessage());
    commit.setTreeId(tree);
    if (oldSuperProjectCommit.getParentCount() > 0) {
      commit.setParentId(oldSuperProjectCommit.getParent(0));
    }

    final ObjectId commitId = oi.idFor(Constants.OBJ_COMMIT, commit.build());
    oi.insert(commit);

    rfu.setForceUpdate(true);
    rfu.setNewObjectId(commitId);
    rfu.update();

    superPatchToUpdate.setRevision(new RevId(commitId.getName()));
    db.patchSets().update(Collections.singleton(superPatchToUpdate));

    replication.scheduleUpdate(superProjectNameKey, rfu.getName());

    updateSuperChange(RevCommit.parse(commit.build()), superChange);
  }

  private static ObjectId emptyTree(final Repository repo) throws IOException {
    ObjectInserter oi = repo.newObjectInserter();
    try {
      ObjectId id = oi.insert(Constants.OBJ_TREE, new byte[] {});
      oi.flush();
      return id;
    } finally {
      oi.release();
    }
  }
}
