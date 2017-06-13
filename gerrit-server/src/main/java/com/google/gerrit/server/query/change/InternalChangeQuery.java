// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.query.Predicate.and;
import static com.google.gerrit.server.query.Predicate.not;
import static com.google.gerrit.server.query.Predicate.or;
import static com.google.gerrit.server.query.change.ChangeStatusPredicate.open;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.InternalQuery;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class InternalChangeQuery extends InternalQuery<ChangeData> {
  private static Predicate<ChangeData> ref(Branch.NameKey branch) {
    return new RefPredicate(branch.get());
  }

  private static Predicate<ChangeData> change(Change.Key key) {
    return new ChangeIdPredicate(key.get());
  }

  private static Predicate<ChangeData> project(Project.NameKey project) {
    return new ProjectPredicate(project.get());
  }

  private static Predicate<ChangeData> status(Change.Status status) {
    return new ChangeStatusPredicate(status);
  }

  private static Predicate<ChangeData> commit(String id) {
    return new CommitPredicate(id);
  }

  private final ChangeData.Factory changeDataFactory;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  InternalChangeQuery(
      ChangeQueryProcessor queryProcessor,
      ChangeIndexCollection indexes,
      IndexConfig indexConfig,
      ChangeData.Factory changeDataFactory,
      ChangeNotes.Factory notesFactory) {
    super(queryProcessor, indexes, indexConfig);
    this.changeDataFactory = changeDataFactory;
    this.notesFactory = notesFactory;
  }

  @Override
  public InternalChangeQuery setLimit(int n) {
    super.setLimit(n);
    return this;
  }

  @Override
  public InternalChangeQuery enforceVisibility(boolean enforce) {
    super.enforceVisibility(enforce);
    return this;
  }

  @Override
  public InternalChangeQuery setRequestedFields(Set<String> fields) {
    super.setRequestedFields(fields);
    return this;
  }

  @Override
  public InternalChangeQuery noFields() {
    super.noFields();
    return this;
  }

  public List<ChangeData> byKey(Change.Key key) throws OrmException {
    return byKeyPrefix(key.get());
  }

  public List<ChangeData> byKeyPrefix(String prefix) throws OrmException {
    return query(new ChangeIdPredicate(prefix));
  }

  public List<ChangeData> byLegacyChangeId(Change.Id id) throws OrmException {
    return query(new LegacyChangeIdPredicate(id));
  }

  public List<ChangeData> byLegacyChangeIds(Collection<Change.Id> ids) throws OrmException {
    List<Predicate<ChangeData>> preds = new ArrayList<>(ids.size());
    for (Change.Id id : ids) {
      preds.add(new LegacyChangeIdPredicate(id));
    }
    return query(or(preds));
  }

  public List<ChangeData> byBranchKey(Branch.NameKey branch, Change.Key key) throws OrmException {
    return query(and(ref(branch), project(branch.getParentKey()), change(key)));
  }

  public List<ChangeData> byProject(Project.NameKey project) throws OrmException {
    return query(project(project));
  }

  public List<ChangeData> byBranchOpen(Branch.NameKey branch) throws OrmException {
    return query(and(ref(branch), project(branch.getParentKey()), open()));
  }

  public List<ChangeData> byBranchNew(Branch.NameKey branch) throws OrmException {
    return query(and(ref(branch), project(branch.getParentKey()), status(Change.Status.NEW)));
  }

  public Iterable<ChangeData> byCommitsOnBranchNotMerged(
      Repository repo, ReviewDb db, Branch.NameKey branch, Collection<String> hashes)
      throws OrmException, IOException {
    return byCommitsOnBranchNotMerged(
        repo,
        db,
        branch,
        hashes,
        // Account for all commit predicates plus ref, project, status.
        indexConfig.maxTerms() - 3);
  }

  @VisibleForTesting
  Iterable<ChangeData> byCommitsOnBranchNotMerged(
      Repository repo,
      ReviewDb db,
      Branch.NameKey branch,
      Collection<String> hashes,
      int indexLimit)
      throws OrmException, IOException {
    if (hashes.size() > indexLimit) {
      return byCommitsOnBranchNotMergedFromDatabase(repo, db, branch, hashes);
    }
    return byCommitsOnBranchNotMergedFromIndex(branch, hashes);
  }

  private Iterable<ChangeData> byCommitsOnBranchNotMergedFromDatabase(
      Repository repo, ReviewDb db, Branch.NameKey branch, Collection<String> hashes)
      throws OrmException, IOException {
    Set<Change.Id> changeIds = Sets.newHashSetWithExpectedSize(hashes.size());
    String lastPrefix = null;
    for (Ref ref : repo.getRefDatabase().getRefs(RefNames.REFS_CHANGES).values()) {
      String r = ref.getName();
      if ((lastPrefix != null && r.startsWith(lastPrefix))
          || !hashes.contains(ref.getObjectId().name())) {
        continue;
      }
      Change.Id id = Change.Id.fromRef(r);
      if (id == null) {
        continue;
      }
      if (changeIds.add(id)) {
        lastPrefix = r.substring(0, r.lastIndexOf('/'));
      }
    }

    List<ChangeNotes> notes =
        notesFactory.create(
            db,
            branch.getParentKey(),
            changeIds,
            cn -> {
              Change c = cn.getChange();
              return c.getDest().equals(branch) && c.getStatus() != Change.Status.MERGED;
            });
    return Lists.transform(notes, n -> changeDataFactory.create(db, n));
  }

  private Iterable<ChangeData> byCommitsOnBranchNotMergedFromIndex(
      Branch.NameKey branch, Collection<String> hashes) throws OrmException {
    return query(
        and(
            ref(branch),
            project(branch.getParentKey()),
            not(status(Change.Status.MERGED)),
            or(commits(hashes))));
  }

  private static List<Predicate<ChangeData>> commits(Collection<String> hashes) {
    List<Predicate<ChangeData>> commits = new ArrayList<>(hashes.size());
    for (String s : hashes) {
      commits.add(commit(s));
    }
    return commits;
  }

  public List<ChangeData> byProjectOpen(Project.NameKey project) throws OrmException {
    return query(and(project(project), open()));
  }

  public List<ChangeData> byTopicOpen(String topic) throws OrmException {
    return query(and(new ExactTopicPredicate(topic), open()));
  }

  public List<ChangeData> byCommit(ObjectId id) throws OrmException {
    return byCommit(id.name());
  }

  public List<ChangeData> byCommit(String hash) throws OrmException {
    return query(commit(hash));
  }

  public List<ChangeData> byProjectCommit(Project.NameKey project, ObjectId id)
      throws OrmException {
    return byProjectCommit(project, id.name());
  }

  public List<ChangeData> byProjectCommit(Project.NameKey project, String hash)
      throws OrmException {
    return query(and(project(project), commit(hash)));
  }

  public List<ChangeData> byProjectCommits(Project.NameKey project, List<String> hashes)
      throws OrmException {
    int n = indexConfig.maxTerms() - 1;
    checkArgument(hashes.size() <= n, "cannot exceed %s commits", n);
    return query(and(project(project), or(commits(hashes))));
  }

  public List<ChangeData> byBranchCommit(String project, String branch, String hash)
      throws OrmException {
    return query(and(new ProjectPredicate(project), new RefPredicate(branch), commit(hash)));
  }

  public List<ChangeData> byBranchCommit(Branch.NameKey branch, String hash) throws OrmException {
    return byBranchCommit(branch.getParentKey().get(), branch.get(), hash);
  }

  public List<ChangeData> bySubmissionId(String cs) throws OrmException {
    if (Strings.isNullOrEmpty(cs)) {
      return Collections.emptyList();
    }
    return query(new SubmissionIdPredicate(cs));
  }

  public List<ChangeData> byProjectGroups(Project.NameKey project, Collection<String> groups)
      throws OrmException {
    List<GroupPredicate> groupPredicates = new ArrayList<>(groups.size());
    for (String g : groups) {
      groupPredicates.add(new GroupPredicate(g));
    }
    return query(and(project(project), or(groupPredicates)));
  }
}
