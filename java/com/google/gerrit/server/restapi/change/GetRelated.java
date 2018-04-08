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

package com.google.gerrit.server.restapi.change;

import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.util.common.CommonConverters;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class GetRelated implements RestReadView<RevisionResource> {
  private final Provider<ReviewDb> db;
  private final Provider<InternalChangeQuery> queryProvider;
  private final PatchSetUtil psUtil;
  private final RelatedChangesSorter sorter;
  private final IndexConfig indexConfig;

  @Inject
  GetRelated(
      Provider<ReviewDb> db,
      Provider<InternalChangeQuery> queryProvider,
      PatchSetUtil psUtil,
      RelatedChangesSorter sorter,
      IndexConfig indexConfig) {
    this.db = db;
    this.queryProvider = queryProvider;
    this.psUtil = psUtil;
    this.sorter = sorter;
    this.indexConfig = indexConfig;
  }

  @Override
  public RelatedInfo apply(RevisionResource rsrc)
      throws RepositoryNotFoundException, IOException, OrmException, NoSuchProjectException,
          PermissionBackendException {
    RelatedInfo relatedInfo = new RelatedInfo();
    relatedInfo.changes = getRelated(rsrc);
    return relatedInfo;
  }

  private List<ChangeAndCommit> getRelated(RevisionResource rsrc)
      throws OrmException, IOException, PermissionBackendException {
    Set<String> groups = getAllGroups(rsrc.getNotes(), db.get(), psUtil);
    if (groups.isEmpty()) {
      return Collections.emptyList();
    }

    List<ChangeData> cds =
        InternalChangeQuery.byProjectGroups(
            queryProvider, indexConfig, rsrc.getChange().getProject(), groups);
    if (cds.isEmpty()) {
      return Collections.emptyList();
    }
    if (cds.size() == 1 && cds.get(0).getId().equals(rsrc.getChange().getId())) {
      return Collections.emptyList();
    }
    List<ChangeAndCommit> result = new ArrayList<>(cds.size());

    boolean isEdit = rsrc.getEdit().isPresent();
    PatchSet basePs = isEdit ? rsrc.getEdit().get().getBasePatchSet() : rsrc.getPatchSet();

    reloadChangeIfStale(cds, basePs);

    for (RelatedChangesSorter.PatchSetData d : sorter.sort(cds, basePs)) {
      PatchSet ps = d.patchSet();
      RevCommit commit;
      if (isEdit && ps.getId().equals(basePs.getId())) {
        // Replace base of an edit with the edit itself.
        ps = rsrc.getPatchSet();
        commit = rsrc.getEdit().get().getEditCommit();
      } else {
        commit = d.commit();
      }
      result.add(new ChangeAndCommit(rsrc.getProject(), d.data().change(), ps, commit));
    }

    if (result.size() == 1) {
      ChangeAndCommit r = result.get(0);
      if (r.commit != null && r.commit.commit.equals(rsrc.getPatchSet().getRevision().get())) {
        return Collections.emptyList();
      }
    }
    return result;
  }

  @VisibleForTesting
  public static Set<String> getAllGroups(ChangeNotes notes, ReviewDb db, PatchSetUtil psUtil)
      throws OrmException {
    return psUtil
        .byChange(db, notes)
        .stream()
        .flatMap(ps -> ps.getGroups().stream())
        .collect(toSet());
  }

  private void reloadChangeIfStale(List<ChangeData> cds, PatchSet wantedPs) throws OrmException {
    for (ChangeData cd : cds) {
      if (cd.getId().equals(wantedPs.getId().getParentKey())) {
        if (cd.patchSet(wantedPs.getId()) == null) {
          cd.reloadChange();
        }
      }
    }
  }

  public static class RelatedInfo {
    public List<ChangeAndCommit> changes;
  }

  public static class ChangeAndCommit {
    public String project;
    public String changeId;
    public CommitInfo commit;
    public Integer _changeNumber;
    public Integer _revisionNumber;
    public Integer _currentRevisionNumber;
    public String status;

    public ChangeAndCommit() {}

    ChangeAndCommit(
        Project.NameKey project, @Nullable Change change, @Nullable PatchSet ps, RevCommit c) {
      this.project = project.get();

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
          .add("project", project)
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
