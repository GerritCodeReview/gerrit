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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.InternalQuery;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Query wrapper for the change index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class InternalChangeQuery extends InternalQuery<ChangeData, InternalChangeQuery> {
  private static Predicate<ChangeData> ref(BranchNameKey branch) {
    return ChangePredicates.ref(branch.branch());
  }

  private static Predicate<ChangeData> change(Change.Key key) {
    return ChangePredicates.idPrefix(key.get());
  }

  private static Predicate<ChangeData> project(Project.NameKey project) {
    return ChangePredicates.project(project);
  }

  private static Predicate<ChangeData> status(Change.Status status) {
    return ChangeStatusPredicate.forStatus(status);
  }

  private static Predicate<ChangeData> commit(String id) {
    return ChangePredicates.commitPrefix(id);
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

  public List<ChangeData> byKey(Change.Key key) {
    return byKeyPrefix(key.get());
  }

  public List<ChangeData> byKeyPrefix(String prefix) {
    return query(ChangePredicates.idPrefix(prefix));
  }

  public List<ChangeData> byLegacyChangeId(Change.Id id) {
    return query(ChangePredicates.idStr(id));
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  public List<ChangeData> byLegacyChangeIds(Collection<Change.Id> ids) {
    List<Predicate<ChangeData>> preds = new ArrayList<>(ids.size());
    for (Change.Id id : ids) {
      preds.add(ChangePredicates.idStr(id));
    }
    return query(or(preds));
  }

  public List<ChangeData> byBranchKey(BranchNameKey branch, Change.Key key) {
    return query(byBranchKeyPred(branch, key));
  }

  private static Predicate<ChangeData> byBranchKeyPred(BranchNameKey branch, Change.Key key) {
    return and(ref(branch), project(branch.project()), change(key));
  }

  public List<ChangeData> byProject(Project.NameKey project) {
    return query(project(project));
  }

  public List<ChangeData> byBranchOpen(BranchNameKey branch) {
    return query(and(ref(branch), project(branch.project()), open()));
  }

  public List<ChangeData> byBranchNew(BranchNameKey branch) {
    return query(and(ref(branch), project(branch.project()), status(Change.Status.NEW)));
  }

  public Iterable<ChangeData> byCommitsOnBranchNotMerged(
      Repository repo, BranchNameKey branch, Collection<String> hashes) throws IOException {
    return byCommitsOnBranchNotMerged(
        repo,
        branch,
        hashes,
        // Account for all commit predicates plus ref, project, status.
        indexConfig.maxTerms() - 3);
  }

  @VisibleForTesting
  Iterable<ChangeData> byCommitsOnBranchNotMerged(
      Repository repo, BranchNameKey branch, Collection<String> hashes, int indexLimit)
      throws IOException {
    if (hashes.size() > indexLimit || !indexes.getSearchIndex().isEnabled()) {
      return byCommitsOnBranchNotMergedFromDatabase(repo, branch, hashes);
    }
    return byCommitsOnBranchNotMergedFromIndex(branch, hashes);
  }

  private Iterable<ChangeData> byCommitsOnBranchNotMergedFromDatabase(
      Repository repo, BranchNameKey branch, Collection<String> hashes) throws IOException {
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
            repo,
            branch.project(),
            changeIds,
            cn -> {
              Change c = cn.getChange();
              return c.getDest().equals(branch) && !c.isMerged();
            });
    return Lists.transform(notes, n -> changeDataFactory.create(n));
  }

  private Iterable<ChangeData> byCommitsOnBranchNotMergedFromIndex(
      BranchNameKey branch, Collection<String> hashes) {
    return query(
        and(
            ref(branch),
            project(branch.project()),
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

  public List<ChangeData> byProjectOpen(Project.NameKey project) {
    return query(and(project(project), open()));
  }

  public List<ChangeData> byTopicOpen(String topic) {
    return query(and(ChangePredicates.exactTopic(topic), open()));
  }

  public List<ChangeData> byCommit(ObjectId id) {
    return byCommit(id.name());
  }

  public List<ChangeData> byCommit(String hash) {
    return query(commit(hash));
  }

  public List<ChangeData> byProjectCommit(Project.NameKey project, ObjectId id) {
    return byProjectCommit(project, id.name());
  }

  public List<ChangeData> byProjectCommit(Project.NameKey project, String hash) {
    return query(and(project(project), commit(hash)));
  }

  public List<ChangeData> byProjectCommits(Project.NameKey project, List<String> hashes) {
    int n = indexConfig.maxTerms() - 1;
    checkArgument(hashes.size() <= n, "cannot exceed %s commits", n);
    return query(and(project(project), or(commits(hashes))));
  }

  public List<ChangeData> byBranchCommit(String project, String branch, String hash) {
    return query(byBranchCommitPred(project, branch, hash));
  }

  public List<ChangeData> byBranchCommit(BranchNameKey branch, String hash) {
    return byBranchCommit(branch.project().get(), branch.branch(), hash);
  }

  public List<ChangeData> byBranchCommitOpen(String project, String branch, String hash) {
    return query(and(byBranchCommitPred(project, branch, hash), open()));
  }

  public static Predicate<ChangeData> byBranchCommitOpenPred(
      Project.NameKey project, String branch, String hash) {
    return and(byBranchCommitPred(project.get(), branch, hash), open());
  }

  private static Predicate<ChangeData> byBranchCommitPred(
      String project, String branch, String hash) {
    return and(
        ChangePredicates.project(Project.nameKey(project)),
        ChangePredicates.ref(branch),
        commit(hash));
  }

  public List<ChangeData> bySubmissionId(String cs) {
    if (Strings.isNullOrEmpty(cs)) {
      return Collections.emptyList();
    }
    return query(ChangePredicates.submissionId(cs));
  }

  private static Predicate<ChangeData> byProjectGroupsPredicate(
      IndexConfig indexConfig, Project.NameKey project, Collection<String> groups) {
    int n = indexConfig.maxTerms() - 1;
    checkArgument(groups.size() <= n, "cannot exceed %s groups", n);
    List<GroupPredicate> groupPredicates = new ArrayList<>(groups.size());
    for (String g : groups) {
      groupPredicates.add(new GroupPredicate(g));
    }
    return and(project(project), or(groupPredicates));
  }

  public static ImmutableList<ChangeData> byProjectGroups(
      Provider<InternalChangeQuery> queryProvider,
      IndexConfig indexConfig,
      Project.NameKey project,
      Collection<String> groups) {
    // These queries may be complex along multiple dimensions:
    //  * Many groups per change, if there are very many patch sets. This requires partitioning the
    //    list of predicates and combining results.
    //  * Many changes with the same set of groups, if the relation chain is very long. This
    //    requires querying exhaustively with pagination.
    // For both cases, we need to invoke the queryProvider multiple times, since each
    // InternalChangeQuery is single-use.

    Supplier<InternalChangeQuery> querySupplier = () -> queryProvider.get().enforceVisibility(true);
    int batchSize = indexConfig.maxTerms() - 1;
    if (groups.size() <= batchSize) {
      return queryExhaustively(
          querySupplier, byProjectGroupsPredicate(indexConfig, project, groups));
    }
    Set<Change.Id> seen = new HashSet<>();
    ImmutableList.Builder<ChangeData> result = ImmutableList.builder();
    for (List<String> part : Iterables.partition(groups, batchSize)) {
      for (ChangeData cd :
          queryExhaustively(querySupplier, byProjectGroupsPredicate(indexConfig, project, part))) {
        if (!seen.add(cd.getId())) {
          result.add(cd);
        }
      }
    }
    return result.build();
  }
}
