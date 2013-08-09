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

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.AccountPatchReview;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

class ReviewedFlagCopier implements Runnable {
  private static final Logger log = LoggerFactory
      .getLogger(ReviewedFlagCopier.class);

  interface Factory {
    ReviewedFlagCopier create(PatchSet.Id psId);
  }

  @Singleton
  static class Queue {
    private final Factory factory;
    private final WorkQueue q;

    @Inject
    Queue(Factory factory, WorkQueue q) {
      this.factory = factory;
      this.q = q;
    }

    Future<?> copy(PatchSet.Id id) {
      return q.getDefaultQueue().submit(factory.create(id));
    }
  }

  private final SchemaFactory<ReviewDb> dbFactory;
  private final GitRepositoryManager gitManager;
  private final PatchListCache patchListCache;
  private final PatchSet.Id newPsId;

  @Inject
  ReviewedFlagCopier(
      SchemaFactory<ReviewDb> dbFactory,
      GitRepositoryManager gitManager,
      PatchListCache patchListCache,
      @Assisted PatchSet.Id newPsId) {
    this.dbFactory = dbFactory;
    this.gitManager = gitManager;
    this.patchListCache = patchListCache;
    this.newPsId = newPsId;
  }

  @Override
  public void run() {
    try {
      ReviewDb db = dbFactory.open();
      try {
        Change change = db.changes().get(newPsId.getParentKey());
        if (change != null) {
          Repository git = gitManager.openRepository(change.getProject());
          try {
            ObjectReader reader = git.newObjectReader();
            try {
              copy(db, git, reader, change);
            } finally {
              reader.release();
            }
          } finally {
            git.close();
          }
        }
      } finally {
        db.close();
      }
    } catch (IOException e) {
      log.warn("Cannot copy reviewed flags to new revision", e);
    } catch (OrmException e) {
      log.warn("Cannot copy reviewed flags to new revision", e);
    }
  }

  private void copy(ReviewDb db, Repository git, ObjectReader reader, Change change)
      throws OrmException, IOException {
    Map<PatchSet.Id, PatchSet> all = db.patchSets()
        .toMap(db.patchSets().byChange(change.getId()));
    Multimap<RevisionPath, Account.Id> reviewed = reviewed(db, all.keySet());
    if (reviewed.isEmpty()) {
      return;
    }

    Map<PatchSet.Id, PatchList> patchLists = Maps.newHashMap();
    for (PatchSet.Id p : uniquePatchSets(reviewed)) {
      try {
        patchLists.put(p, patchListCache.get(change, all.get(p)));
      } catch (PatchListNotAvailableException e) {
        log.warn("Cannot load PatchList for " + p, e);
      }
    }

    PatchList currentList = patchLists.remove(newPsId);
    if (currentList == null) {
      return;
    }

    RevWalk rw = new RevWalk(reader);
    Multimap<ObjectId, RevisionPath> canCopy = indexObjects(rw, patchLists);
    List<AccountPatchReview> inserts = Lists.newArrayList();
    RevCommit bCommit = rw.parseCommit(currentList.getNewId());
    RevTree bTree = bCommit.getTree();
    for (PatchListEntry p : currentList.getPatches()) {
      if (!Patch.COMMIT_MSG.equals(p.getNewName())) {
        TreeWalk tw = TreeWalk.forPath(reader, p.getNewName(), bTree);
        if (tw != null) {
          ObjectId id = tw.getObjectId(0);

          Set<Account.Id> toCopy = Sets.newHashSet();
          for(RevisionPath m : canCopy.get(id)) {
            toCopy.addAll(reviewed.get(m));
          }
          for (Account.Id u : toCopy) {
            inserts.add(new AccountPatchReview(
                new Patch.Key(newPsId, p.getNewName()),
                u));
          }
        }
      }
    }

    db.accountPatchReviews().insert(inserts);
  }

  private Multimap<RevisionPath, Id> reviewed(ReviewDb db,
      Set<PatchSet.Id> ps) throws OrmException {
    List<ResultSet<AccountPatchReview>> futures =
        Lists.newArrayListWithCapacity(ps.size());
    for (PatchSet.Id p : ps) {
      futures.add(db.accountPatchReviews().byPatchSet(p));
    }

    Multimap<RevisionPath, Account.Id> prior = ArrayListMultimap.create();
    for (ResultSet<AccountPatchReview> rs : futures) {
      for (AccountPatchReview r : rs) {
        Account.Id u = r.getKey().getParentKey();
        Patch.Key p = r.getKey().getPatchKey();
        prior.put(new RevisionPath(p.getParentKey(), p.get()), u);
      }
    }
    return prior;
  }

  private Multimap<ObjectId, RevisionPath> indexObjects(RevWalk rw,
      Map<PatchSet.Id, PatchList> all) throws IOException {
    ObjectReader reader = rw.getObjectReader();
    Multimap<ObjectId, RevisionPath> canCopy = ArrayListMultimap.create();
    for (Map.Entry<PatchSet.Id, PatchList> e : all.entrySet()) {
      PatchList list = e.getValue();
      RevTree t = rw.parseCommit(list.getNewId()).getTree();
      for (PatchListEntry p : list.getPatches()) {
        String path = p.getNewName();
        if (!Patch.COMMIT_MSG.equals(path)) {
          TreeWalk tw = TreeWalk.forPath(reader, path, t);
          if (tw != null) {
            canCopy.put(tw.getObjectId(0), new RevisionPath(e.getKey(), path));
          }
        }
      }
    }
    return canCopy;
  }

  private static Set<PatchSet.Id> uniquePatchSets(
      Multimap<RevisionPath, Id> reviewed) {
    return Sets.newHashSet(Iterables.transform(
        reviewed.keySet(),
        new Function<RevisionPath, PatchSet.Id>() {
          @Override
          public PatchSet.Id apply(RevisionPath input) {
            return input.id;
          }
        }));
  }

  private static class RevisionPath {
    final PatchSet.Id id;
    final String path;

    RevisionPath(PatchSet.Id id, String path) {
      this.id = id;
      this.path = path;
    }

    @Override
    public int hashCode() {
      return id.hashCode() * 31 + path.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof RevisionPath) {
        RevisionPath b = (RevisionPath) o;
        return id.equals(b.id) && path.equals(b.path);
      }
      return false;
    }
  }
}
