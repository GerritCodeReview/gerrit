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

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.RelatedChangesSorter.PatchSetData;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class GetRelated implements RestReadView<RevisionResource> {
  private final Provider<ReviewDb> db;
  private final Provider<InternalChangeQuery> queryProvider;
  private final PatchSetUtil psUtil;
  private final RelatedChangesSorter sorter;

  @Inject
  GetRelated(Provider<ReviewDb> db,
      Provider<InternalChangeQuery> queryProvider,
      PatchSetUtil psUtil,
      RelatedChangesSorter sorter) {
    this.db = db;
    this.queryProvider = queryProvider;
    this.psUtil = psUtil;
    this.sorter = sorter;
  }

  @Override
  public RelatedInfo apply(RevisionResource rsrc)
      throws RepositoryNotFoundException, IOException, OrmException {
    RelatedInfo relatedInfo = new RelatedInfo();
    relatedInfo.changes = getRelated(rsrc);
    return relatedInfo;
  }

  private List<ChangeAndCommit> getRelated(RevisionResource rsrc)
      throws OrmException, IOException {
    Set<String> groups = getAllGroups(rsrc.getNotes());
    if (groups.isEmpty()) {
      return Collections.emptyList();
    }

    List<ChangeData> cds = queryProvider.get()
        .enforceVisibility(true)
        .byProjectGroups(rsrc.getChange().getProject(), groups);
    if (cds.isEmpty()) {
      return Collections.emptyList();
    }
    if (cds.size() == 1
        && cds.get(0).getId().equals(rsrc.getChange().getId())) {
      return Collections.emptyList();
    }
    List<ChangeAndCommit> result = new ArrayList<>(cds.size());

    boolean isEdit = rsrc.getEdit().isPresent();
    PatchSet basePs = isEdit
        ? rsrc.getEdit().get().getBasePatchSet()
        : rsrc.getPatchSet();
    for (PatchSetData d : sorter.sort(cds, basePs)) {
      PatchSet ps = d.patchSet();
      RevCommit commit;
      if (isEdit && ps.getId().equals(basePs.getId())) {
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

  private Set<String> getAllGroups(ChangeNotes notes) throws OrmException {
    Set<String> result = new HashSet<>();
    for (PatchSet ps : psUtil.byChange(db.get(), notes)) {
      result.addAll(ps.getGroups());
    }
    return result;
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
    public String status;

    public ChangeAndCommit() {
    }

    ChangeAndCommit(@Nullable Change change, @Nullable PatchSet ps, RevCommit c) {
      if (change != null) {
        changeId = change.getKey().get();
        _changeNumber = change.getChangeId();
        _revisionNumber = ps != null ? ps.getPatchSetId() : null;
        PatchSet.Id curr = change.currentPatchSetId();
        _currentRevisionNumber = curr != null ? curr.get() : null;
        status = change.getStatus().asChangeStatus().toString();
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
          .add("status", status)
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
