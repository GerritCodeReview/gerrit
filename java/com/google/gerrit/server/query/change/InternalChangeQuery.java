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
import static com.google.gerrit.index.query.Predicate.and;
import static com.google.gerrit.index.query.Predicate.not;
import static com.google.gerrit.index.query.Predicate.or;
import static com.google.gerrit.server.query.change.ChangeStatusPredicate.open;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.InternalQuery;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Query wrapper for the change index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
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
    return ChangeStatusPredicate.forStatus(status);
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

  @SafeVarargs
  @Override
  public final InternalChangeQuery setRequestedFields(FieldDef<ChangeData, ?>... fields) {
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
    return query(byBranchKeyPred(branch, key));
  }

  public List<ChangeData> byBranchKeyOpen(Project.NameKey project, String branch, Change.Key key)
      throws OrmException {
    return query(and(byBranchKeyPred(new Branch.NameKey(project, branch), key), open()));
  }

  public static Predicate<ChangeData> byBranchKeyOpenPred(
      Project.NameKey project, String branch, Change.Key key) {
    return and(byBranchKeyPred(new Branch.NameKey(project, branch), key), open());
  }

  private static Predicate<ChangeData> byBranchKeyPred(Branch.NameKey branch, Change.Key key) {
    return and(ref(branch), project(branch.getParentKey()), change(key));
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
    for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
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
    return query(byBranchCommitPred(project, branch, hash));
  }

  public List<ChangeData> byBranchCommit(Branch.NameKey branch, String hash) throws OrmException {
    return byBranchCommit(branch.getParentKey().get(), branch.get(), hash);
  }

  public List<ChangeData> byBranchCommitOpen(String project, String branch, String hash)
      throws OrmException {
    return query(and(byBranchCommitPred(project, branch, hash), open()));
  }

  public static Predicate<ChangeData> byBranchCommitOpenPred(
      Project.NameKey project, String branch, String hash) {
    return and(byBranchCommitPred(project.get(), branch, hash), open());
  }

  private static Predicate<ChangeData> byBranchCommitPred(
      String project, String branch, String hash) {
    return and(new ProjectPredicate(project), new RefPredicate(branch), commit(hash));
  }

  public List<ChangeData> bySubmissionId(String cs) throws OrmException {
    if (Strings.isNullOrEmpty(cs)) {
      return Collections.emptyList();
    }
    return query(new SubmissionIdPredicate(cs));
  }

  private List<ChangeData> byProjectGroups(Project.NameKey project, Collection<String> groups)
      throws OrmException {
    int n = indexConfig.maxTerms() - 1;
    checkArgument(groups.size() <= n, "cannot exceed %s groups", n);
    List<GroupPredicate> groupPredicates = new ArrayList<>(groups.size());
    for (String g : groups) {
      groupPredicates.add(new GroupPredicate(g));
    }
    return query(and(project(project), or(groupPredicates)));
  }

  // Batching via multiple queries requires passing in a Provider since the underlying
  // QueryProcessor instance is not reusable.
  public static List<ChangeData> byProjectGroups(
      Provider<InternalChangeQuery> queryProvider,
      IndexConfig indexConfig,
      Project.NameKey project,
      Collection<String> groups)
      throws OrmException {
    int batchSize = indexConfig.maxTerms() - 1;
    if (groups.size() <= batchSize) {
      return queryProvider.get().enforceVisibility(true).byProjectGroups(project, groups);
    }
    Set<Change.Id> seen = new HashSet<>();
    List<ChangeData> result = new ArrayList<>();
    for (List<String> part : Iterables.partition(groups, batchSize)) {
      for (ChangeData cd :
          queryProvider.get().enforceVisibility(true).byProjectGroups(project, part)) {
        if (!seen.add(cd.getId())) {
          result.add(cd);
        }
      }
    }
    return result;
  }
}
