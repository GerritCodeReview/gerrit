// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class Schema_108 extends SchemaVersion {
  private final GitRepositoryManager repoManager;

  @Inject
  Schema_108(Provider<Schema_107> prior,
      GitRepositoryManager repoManager) {
    super(prior);
    this.repoManager = repoManager;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    ui.message("Listing all changes ...");
    SetMultimap<Project.NameKey, Change.Id> openByProject =
        getOpenChangesByProject(db);
    ui.message("done");

    ui.message("Updating groups for open changes ...");
    int i = 0;
    for (Map.Entry<Project.NameKey, Collection<Change.Id>> e
        : openByProject.asMap().entrySet()) {
      try (Repository repo = repoManager.openRepository(e.getKey());
          RevWalk rw = new RevWalk(repo)) {
        updateProjectGroups(db, repo, rw, (Set<Change.Id>) e.getValue());
      } catch (IOException err) {
        throw new OrmException(err);
      }
      if (++i % 100 == 0) {
        ui.message("  done " + i + " projects ...");
      }
    }
    ui.message("done");
  }

  private static void updateProjectGroups(ReviewDb db, Repository repo,
      RevWalk rw, Set<Change.Id> changes) throws OrmException, IOException {
    // Match sorting in ReceiveCommits.
    rw.reset();
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.REVERSE, true);

    RefDatabase refdb = repo.getRefDatabase();
    for (Ref ref : refdb.getRefs(Constants.R_HEADS).values()) {
      RevCommit c = maybeParseCommit(rw, ref.getObjectId());
      if (c != null) {
        rw.markUninteresting(c);
      }
    }

    Multimap<ObjectId, Ref> changeRefsBySha = ArrayListMultimap.create();
    Multimap<ObjectId, PatchSet.Id> patchSetsBySha = ArrayListMultimap.create();
    for (Ref ref : refdb.getRefs(RefNames.REFS_CHANGES).values()) {
      ObjectId id = ref.getObjectId();
      if (ref.getObjectId() == null) {
        continue;
      }
      id = id.copy();
      changeRefsBySha.put(id, ref);
      PatchSet.Id psId = PatchSet.Id.fromRef(ref.getName());
      if (psId != null && changes.contains(psId.getParentKey())) {
        patchSetsBySha.put(id, psId);
        RevCommit c = maybeParseCommit(rw, id);
        if (c != null) {
          rw.markStart(c);
        }
      }
    }

    GroupCollector collector = new GroupCollector(changeRefsBySha, db);
    RevCommit c;
    while ((c = rw.next()) != null) {
      collector.visit(c);
    }

    updateGroups(db, collector, patchSetsBySha);
  }

  private static void updateGroups(ReviewDb db, GroupCollector collector,
      Multimap<ObjectId, PatchSet.Id> patchSetsBySha) throws OrmException {
    Map<PatchSet.Id, PatchSet> patchSets =
        db.patchSets().toMap(db.patchSets().get(patchSetsBySha.values()));
    for (Map.Entry<ObjectId, Collection<String>> e
        : collector.getGroups().asMap().entrySet()) {
      for (PatchSet.Id psId : patchSetsBySha.get(e.getKey())) {
        PatchSet ps = patchSets.get(psId);
        if (ps != null) {
          ps.setGroups(e.getValue());
        }
      }
    }

    db.patchSets().update(patchSets.values());
  }

  private SetMultimap<Project.NameKey, Change.Id> getOpenChangesByProject(
      ReviewDb db) throws OrmException {
    SetMultimap<Project.NameKey, Change.Id> openByProject =
        HashMultimap.create();
    for (Change c : db.changes().all()) {
      if (c.getStatus().isOpen()) {
        openByProject.put(c.getProject(), c.getId());
      }
    }
    return openByProject;
  }

  private static RevCommit maybeParseCommit(RevWalk rw, ObjectId id)
      throws IOException {
    if (id == null) {
      return null;
    }
    RevObject obj = rw.parseAny(id);
    return (obj instanceof RevCommit) ? (RevCommit) obj : null;
  }
}
