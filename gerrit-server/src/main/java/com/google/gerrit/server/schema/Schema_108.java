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

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

public class Schema_108 extends SchemaVersion {
  private final GitRepositoryManager repoManager;

  @Inject
  Schema_108(Provider<Schema_107> prior, GitRepositoryManager repoManager) {
    super(prior);
    this.repoManager = repoManager;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    ui.message("Listing all changes ...");
    SetMultimap<Project.NameKey, Change.Id> openByProject = getOpenChangesByProject(db, ui);
    ui.message("done");

    ui.message("Updating groups for open changes ...");
    int i = 0;
    for (Map.Entry<Project.NameKey, Collection<Change.Id>> e : openByProject.asMap().entrySet()) {
      try (Repository repo = repoManager.openRepository(e.getKey());
          RevWalk rw = new RevWalk(repo)) {
        updateProjectGroups(db, repo, rw, (Set<Change.Id>) e.getValue(), ui);
      } catch (IOException | NoSuchChangeException err) {
        throw new OrmException(err);
      }
      if (++i % 100 == 0) {
        ui.message("  done " + i + " projects ...");
      }
    }
    ui.message("done");
  }

  private void updateProjectGroups(
      ReviewDb db, Repository repo, RevWalk rw, Set<Change.Id> changes, UpdateUI ui)
      throws OrmException, IOException, NoSuchChangeException {
    // Match sorting in ReceiveCommits.
    rw.reset();
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.REVERSE, true);

    RefDatabase refdb = repo.getRefDatabase();
    for (Ref ref : refdb.getRefs(Constants.R_HEADS).values()) {
      RevCommit c = maybeParseCommit(rw, ref.getObjectId(), ui);
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
        RevCommit c = maybeParseCommit(rw, id, ui);
        if (c != null) {
          rw.markStart(c);
        }
      }
    }

    GroupCollector collector = GroupCollector.createForSchemaUpgradeOnly(changeRefsBySha, db);
    RevCommit c;
    while ((c = rw.next()) != null) {
      collector.visit(c);
    }

    updateGroups(db, collector, patchSetsBySha);
  }

  private static void updateGroups(
      ReviewDb db, GroupCollector collector, Multimap<ObjectId, PatchSet.Id> patchSetsBySha)
      throws OrmException, NoSuchChangeException {
    Map<PatchSet.Id, PatchSet> patchSets =
        db.patchSets().toMap(db.patchSets().get(patchSetsBySha.values()));
    for (Map.Entry<ObjectId, Collection<String>> e : collector.getGroups().asMap().entrySet()) {
      for (PatchSet.Id psId : patchSetsBySha.get(e.getKey())) {
        PatchSet ps = patchSets.get(psId);
        if (ps != null) {
          ps.setGroups(ImmutableList.copyOf(e.getValue()));
        }
      }
    }

    db.patchSets().update(patchSets.values());
  }

  private SetMultimap<Project.NameKey, Change.Id> getOpenChangesByProject(ReviewDb db, UpdateUI ui)
      throws OrmException {
    SortedSet<NameKey> projects = repoManager.list();
    SortedSet<NameKey> nonExistentProjects = Sets.newTreeSet();
    SetMultimap<Project.NameKey, Change.Id> openByProject = HashMultimap.create();
    for (Change c : db.changes().all()) {
      Status status = c.getStatus();
      if (status != null && status.isClosed()) {
        continue;
      }

      NameKey projectKey = c.getProject();
      if (!projects.contains(projectKey)) {
        nonExistentProjects.add(projectKey);
      } else {
        // The old "submitted" state is not supported anymore
        // (thus status is null) but it was an opened state and needs
        // to be migrated as such
        openByProject.put(projectKey, c.getId());
      }
    }

    if (!nonExistentProjects.isEmpty()) {
      ui.message("Detected open changes referring to the following non-existent projects:");
      ui.message(Joiner.on(", ").join(nonExistentProjects));
      ui.message(
          "It is highly recommended to remove\n"
              + "the obsolete open changes, comments and patch-sets from your DB.\n");
    }
    return openByProject;
  }

  private static RevCommit maybeParseCommit(RevWalk rw, ObjectId id, UpdateUI ui)
      throws IOException {
    if (id != null) {
      try {
        RevObject obj = rw.parseAny(id);
        return (obj instanceof RevCommit) ? (RevCommit) obj : null;
      } catch (MissingObjectException moe) {
        ui.message("Missing object: " + id.getName() + "\n");
      }
    }
    return null;
  }
}
