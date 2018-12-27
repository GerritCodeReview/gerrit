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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GetRelated implements RestReadView<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(GetRelated.class);

  private final GitRepositoryManager gitMgr;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  GetRelated(GitRepositoryManager gitMgr, Provider<ReviewDb> db) {
    this.gitMgr = gitMgr;
    this.dbProvider = db;
  }

  @Override
  public RelatedInfo apply(RevisionResource rsrc)
      throws RepositoryNotFoundException, IOException, OrmException {
    Repository git = gitMgr.openRepository(rsrc.getChange().getProject());
    try {
      Ref ref = git.getRef(rsrc.getChange().getDest().get());
      RevWalk rw = new RevWalk(git);
      try {
        RelatedInfo info = new RelatedInfo();
        info.changes = walk(rsrc, rw, ref);
        return info;
      } finally {
        rw.close();
      }
    } finally {
      git.close();
    }
  }

  private List<ChangeAndCommit> walk(RevisionResource rsrc, RevWalk rw, Ref ref)
      throws OrmException, IOException {
    Map<Change.Id, Change> changes = allOpenChanges(rsrc);
    Map<PatchSet.Id, PatchSet> patchSets = allPatchSets(changes.keySet());

    Map<String, PatchSet> commits = Maps.newHashMap();
    for (PatchSet p : patchSets.values()) {
      commits.put(p.getRevision().get(), p);
    }

    RevCommit rev = rw.parseCommit(ObjectId.fromString(
        rsrc.getPatchSet().getRevision().get()));
    rw.sort(RevSort.TOPO);
    rw.markStart(rev);

    if (ref != null && ref.getObjectId() != null) {
      try {
        rw.markUninteresting(rw.parseCommit(ref.getObjectId()));
      } catch (IncorrectObjectTypeException notCommit) {
        // Ignore and treat as new branch.
      }
    }

    Set<Change.Id> added = Sets.newHashSet();
    List<ChangeAndCommit> parents = Lists.newArrayList();
    for (RevCommit c; (c = rw.next()) != null;) {
      PatchSet p = commits.get(c.name());
      Change g = null;
      if (p != null) {
        g = changes.get(p.getId().getParentKey());
        added.add(p.getId().getParentKey());
      }
      parents.add(new ChangeAndCommit(g, p, c));
    }
    List<ChangeAndCommit> list = children(rsrc, rw, changes, patchSets, added);
    list.addAll(parents);

    if (list.size() == 1) {
      ChangeAndCommit r = list.get(0);
      if (r._changeNumber != null && r._revisionNumber != null
          && r._changeNumber == rsrc.getChange().getChangeId()
          && r._revisionNumber == rsrc.getPatchSet().getPatchSetId()) {
        return Collections.emptyList();
      }
    }
    return list;
  }

  private Map<Change.Id, Change> allOpenChanges(RevisionResource rsrc)
      throws OrmException {
    ReviewDb db = dbProvider.get();
    return db.changes().toMap(
        db.changes().byBranchOpenAll(rsrc.getChange().getDest()));
  }

  private Map<PatchSet.Id, PatchSet> allPatchSets(Collection<Change.Id> ids)
      throws OrmException {
    int n = ids.size();
    ReviewDb db = dbProvider.get();
    List<ResultSet<PatchSet>> t = Lists.newArrayListWithCapacity(n);
    for (Change.Id id : ids) {
      t.add(db.patchSets().byChange(id));
    }

    Map<PatchSet.Id, PatchSet> r = Maps.newHashMapWithExpectedSize(n * 2);
    for (ResultSet<PatchSet> rs : t) {
      for (PatchSet p : rs) {
        r.put(p.getId(), p);
      }
    }
    return r;
  }

  private List<ChangeAndCommit> children(RevisionResource rsrc, RevWalk rw,
      Map<Change.Id, Change> changes, Map<PatchSet.Id, PatchSet> patchSets,
      Set<Change.Id> added)
      throws OrmException, IOException {
    // children is a map of parent commit name to PatchSet built on it.
    Multimap<String, PatchSet.Id> children = allChildren(changes.keySet());

    RevFlag seenCommit = rw.newFlag("seenCommit");
    LinkedList<String> q = Lists.newLinkedList();
    seedQueue(rsrc, rw, seenCommit, patchSets, q);

    ProjectControl projectCtl = rsrc.getControl().getProjectControl();
    Set<Change.Id> seenChange = Sets.newHashSet();
    List<ChangeAndCommit> graph = Lists.newArrayList();
    while (!q.isEmpty()) {
      String id = q.remove();

      // For every matching change find the most recent patch set.
      Map<Change.Id, PatchSet.Id> matches = Maps.newHashMap();
      for (PatchSet.Id psId : children.get(id)) {
        PatchSet.Id e = matches.get(psId.getParentKey());
        if ((e == null || e.get() < psId.get())
            && isVisible(projectCtl, changes, patchSets, psId))  {
          matches.put(psId.getParentKey(), psId);
        }
      }

      for (Map.Entry<Change.Id, PatchSet.Id> e : matches.entrySet()) {
        Change change = changes.get(e.getKey());
        PatchSet ps = patchSets.get(e.getValue());
        if (change == null || ps == null || !seenChange.add(e.getKey())) {
          continue;
        }

        RevCommit c = rw.parseCommit(ObjectId.fromString(
            ps.getRevision().get()));
        if (!c.has(seenCommit)) {
          c.add(seenCommit);
          q.addFirst(ps.getRevision().get());
          if (added.add(ps.getId().getParentKey())) {
            rw.parseBody(c);
            graph.add(new ChangeAndCommit(change, ps, c));
          }
        }
      }
    }
    Collections.reverse(graph);
    return graph;
  }

  private boolean isVisible(ProjectControl projectCtl,
      Map<Change.Id, Change> changes,
      Map<PatchSet.Id, PatchSet> patchSets,
      PatchSet.Id psId) throws OrmException {
    Change c = changes.get(psId.getParentKey());
    PatchSet ps = patchSets.get(psId);
    if (c != null && ps != null) {
      ChangeControl ctl = projectCtl.controlFor(c);
      return ctl.isVisible(dbProvider.get())
          && ctl.isPatchVisible(ps, dbProvider.get());
    }
    return false;
  }

  private void seedQueue(RevisionResource rsrc, RevWalk rw,
      RevFlag seenCommit, Map<PatchSet.Id, PatchSet> patchSets,
      LinkedList<String> q) throws IOException {
    RevCommit tip = rw.parseCommit(ObjectId.fromString(
        rsrc.getPatchSet().getRevision().get()));
    tip.add(seenCommit);
    q.add(tip.name());

    Change.Id cId = rsrc.getChange().getId();
    for (PatchSet p : patchSets.values()) {
      if (cId.equals(p.getId().getParentKey())) {
        try {
          RevCommit c = rw.parseCommit(ObjectId.fromString(
              p.getRevision().get()));
          if (!c.has(seenCommit)) {
            c.add(seenCommit);
            q.add(c.name());
          }
        } catch (IOException e) {
          log.warn(String.format(
              "Cannot read patch set %d of %d",
              p.getPatchSetId(), cId.get()), e);
        }
      }
    }
  }

  private Multimap<String, PatchSet.Id> allChildren(Collection<Change.Id> ids)
      throws OrmException {
    ReviewDb db = dbProvider.get();
    List<ResultSet<PatchSetAncestor>> t =
        Lists.newArrayListWithCapacity(ids.size());
    for (Change.Id id : ids) {
      t.add(db.patchSetAncestors().byChange(id));
    }

    Multimap<String, PatchSet.Id> r = ArrayListMultimap.create();
    for (ResultSet<PatchSetAncestor> rs : t) {
      for (PatchSetAncestor a : rs) {
        r.put(a.getAncestorRevision().get(), a.getPatchSet());
      }
    }
    return r;
  }

  private static GitPerson toGitPerson(PersonIdent id) {
    GitPerson p = new GitPerson();
    p.name = id.getName();
    p.email = id.getEmailAddress();
    p.date = new Timestamp(id.getWhen().getTime());
    p.tz = id.getTimeZoneOffset();
    return p;
  }

  public static class RelatedInfo {
    public List<ChangeAndCommit> changes;
  }

  public static class ChangeAndCommit {
    public String changeId;
    public CommitInfo commit;
    public Integer _changeNumber;
    public Integer _revisionNumber;
    public Integer _currentRevisionNumber;

    ChangeAndCommit(@Nullable Change change, @Nullable PatchSet ps, RevCommit c) {
      if (change != null) {
        changeId = change.getKey().get();
        _changeNumber = change.getChangeId();
        _revisionNumber = ps != null ? ps.getPatchSetId() : null;
        PatchSet.Id curr = change.currentPatchSetId();
        _currentRevisionNumber = curr != null ? curr.get() : null;
      }

      commit = new CommitInfo();
      commit.commit = c.name();
      commit.parents = Lists.newArrayListWithCapacity(c.getParentCount());
      for (int i = 0; i < c.getParentCount(); i++) {
        CommitInfo p = new CommitInfo();
        p.commit = c.getParent(i).name();
        commit.parents.add(p);
      }
      commit.author = toGitPerson(c.getAuthorIdent());
      commit.subject = c.getShortMessage();
    }
  }
}
