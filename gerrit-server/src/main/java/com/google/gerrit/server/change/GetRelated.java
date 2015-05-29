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

import static com.google.gerrit.server.index.ChangeField.GROUP;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.change.WalkSorter.PatchSetData;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class GetRelated implements RestReadView<RevisionResource> {
  private final Provider<ReviewDb> db;
  private final GetRelatedByAncestors byAncestors;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<WalkSorter> sorter;
  private final IndexCollection indexes;
  private final boolean byAncestorsOnly;

  @Inject
  GetRelated(Provider<ReviewDb> db,
      @GerritServerConfig Config cfg,
      GetRelatedByAncestors byAncestors,
      Provider<InternalChangeQuery> queryProvider,
      Provider<WalkSorter> sorter,
      IndexCollection indexes) {
    this.db = db;
    this.byAncestors = byAncestors;
    this.queryProvider = queryProvider;
    this.sorter = sorter;
    this.indexes = indexes;
    byAncestorsOnly =
        cfg.getBoolean("change", null, "getRelatedByAncestors", false);
  }

  @Override
  public RelatedInfo apply(RevisionResource rsrc)
      throws RepositoryNotFoundException, IOException, OrmException {
    List<String> thisPatchSetGroups = GroupCollector.getGroups(rsrc);
    if (byAncestorsOnly
        || thisPatchSetGroups == null
        || !indexes.getSearchIndex().getSchema().hasField(GROUP)) {
      return byAncestors.getRelated(rsrc);
    }
    RelatedInfo relatedInfo = new RelatedInfo();
    relatedInfo.changes = getRelated(rsrc, thisPatchSetGroups);
    return relatedInfo;
  }

  private List<ChangeAndCommit> getRelated(RevisionResource rsrc,
      List<String> thisPatchSetGroups) throws OrmException, IOException {
    if (thisPatchSetGroups.isEmpty()) {
      return Collections.emptyList();
    }

    List<ChangeData> cds = queryProvider.get()
        .enforceVisibility(true)
        .byProjectGroups(
            rsrc.getChange().getProject(),
            getAllGroups(rsrc.getChange().getId()));
    List<ChangeAndCommit> result = new ArrayList<>(cds.size());

    PatchSet.Id editBaseId = rsrc.getEdit().isPresent()
        ? rsrc.getEdit().get().getBasePatchSet().getId()
        : null;
    for (PatchSetData d : sorter.get()
        .includePatchSets(choosePatchSets(thisPatchSetGroups, cds))
        .setRetainBody(true)
        .sort(cds)) {
      PatchSet ps = d.patchSet();
      RevCommit commit;
      if (ps.getId().equals(editBaseId)) {
        // Replace base of an edit with the edit itself.
        ps = rsrc.getPatchSet();
        commit = rsrc.getEdit().get().getEditCommit();
      } else {
        commit = d.commit();
      }
      result.add(new ChangeAndCommit(d.data().change(), ps, commit));
    }

    if (result.size() == 1) {
      ChangeAndCommit r = result.get(0);
      if (r.commit != null
          && r.commit.commit.equals(rsrc.getPatchSet().getRevision().get())) {
        return Collections.emptyList();
      }
    }
    return result;
  }

  private Set<String> getAllGroups(Change.Id changeId) throws OrmException {
    Set<String> result = new HashSet<>();
    for (PatchSet ps : db.get().patchSets().byChange(changeId)) {
      List<String> groups = ps.getGroups();
      if (groups != null) {
        result.addAll(groups);
      }
    }
    return result;
  }

  private static Set<PatchSet.Id> choosePatchSets(List<String> groups,
      List<ChangeData> cds) throws OrmException {
    // Prefer the latest patch set matching at least one group from this
    // revision; otherwise, just use the latest patch set overall.
    Set<PatchSet.Id> result = new HashSet<>();
    for (ChangeData cd : cds) {
      Collection<PatchSet> patchSets = cd.patchSets();
      List<PatchSet> sameGroup = new ArrayList<>(patchSets.size());
      for (PatchSet ps : patchSets) {
        if (hasAnyGroup(ps, groups)) {
          sameGroup.add(ps);
        }
      }
      result.add(ChangeUtil.PS_ID_ORDER.max(
          !sameGroup.isEmpty() ? sameGroup : patchSets).getId());
    }
    return result;
  }

  private static boolean hasAnyGroup(PatchSet ps, List<String> groups) {
    if (ps.getGroups() == null) {
      return false;
    }
    // Expected size of each list is 1, so nested linear search is fine.
    for (String g1 : ps.getGroups()) {
      for (String g2 : groups) {
        if (g1.equals(g2)) {
          return true;
        }
      }
    }
    return false;
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

    public ChangeAndCommit() {
    }

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
      commit.author = CommonConverters.toGitPerson(c.getAuthorIdent());
      commit.subject = c.getShortMessage();
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("changeId", changeId)
          .add("commit", toString(commit))
          .add("_changeNumber", _changeNumber)
          .add("_revisionNumber", _revisionNumber)
          .add("_currentRevisionNumber", _currentRevisionNumber)
          .toString();
    }

    private static String toString(CommitInfo commit) {
      return MoreObjects.toStringHelper(commit)
        .add("commit", commit.commit)
        .add("parent", commit.parents)
        .add("author", commit.author)
        .add("committer", commit.committer)
        .add("subject", commit.subject)
        .add("message", commit.message)
        .add("webLinks", commit.webLinks)
        .toString();
    }
  }
}
