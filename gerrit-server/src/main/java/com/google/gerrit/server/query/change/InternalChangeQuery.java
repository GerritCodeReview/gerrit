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
import static com.google.gerrit.server.index.ChangeField.SUBMISSIONID;
import static com.google.gerrit.server.query.Predicate.and;
import static com.google.gerrit.server.query.Predicate.not;
import static com.google.gerrit.server.query.Predicate.or;
import static com.google.gerrit.server.query.change.ChangeStatusPredicate.open;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Execute a single query over changes, for use by Gerrit internals.
 * <p>
 * By default, visibility of returned changes is not enforced (unlike in {@link
 * QueryProcessor}). The methods in this class are not typically used by
 * user-facing paths, but rather by internal callers that need to process all
 * matching results.
 */
public class InternalChangeQuery {
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

  private static Predicate<ChangeData> commit(Schema<ChangeData> schema,
      String id) {
    return new CommitPredicate(schema, id);
  }

  private final IndexConfig indexConfig;
  private final QueryProcessor qp;
  private final IndexCollection indexes;
  private final ChangeData.Factory changeDataFactory;

  @Inject
  InternalChangeQuery(IndexConfig indexConfig,
      QueryProcessor queryProcessor,
      IndexCollection indexes,
      ChangeData.Factory changeDataFactory) {
    this.indexConfig = indexConfig;
    qp = queryProcessor.enforceVisibility(false);
    this.indexes = indexes;
    this.changeDataFactory = changeDataFactory;
  }

  public InternalChangeQuery setLimit(int n) {
    qp.setLimit(n);
    return this;
  }

  public InternalChangeQuery enforceVisibility(boolean enforce) {
    qp.enforceVisibility(enforce);
    return this;
  }

  public InternalChangeQuery setRequestedFields(Set<String> fields) {
    qp.setRequestedFields(fields);
    return this;
  }

  public List<ChangeData> byKey(Change.Key key) throws OrmException {
    return byKeyPrefix(key.get());
  }

  public List<ChangeData> byKeyPrefix(String prefix) throws OrmException {
    return query(new ChangeIdPredicate(prefix));
  }

  public List<ChangeData> byBranchKey(Branch.NameKey branch, Change.Key key)
      throws OrmException {
    return query(and(
        ref(branch),
        project(branch.getParentKey()),
        change(key)));
  }

  public List<ChangeData> byProject(Project.NameKey project)
      throws OrmException {
    return query(project(project));
  }

  public List<ChangeData> byBranchOpen(Branch.NameKey branch)
      throws OrmException {
    return query(and(
        ref(branch),
        project(branch.getParentKey()),
        open()));
  }

  public Iterable<ChangeData> byCommitsOnBranchNotMerged(Repository repo,
      ReviewDb db, Branch.NameKey branch, List<String> hashes)
      throws OrmException, IOException {
    return byCommitsOnBranchNotMerged(repo, db, branch, hashes,
        // Account for all commit predicates plus ref, project, status.
        indexConfig.maxTerms() - 3);
  }

  @VisibleForTesting
  Iterable<ChangeData> byCommitsOnBranchNotMerged(Repository repo, ReviewDb db,
      Branch.NameKey branch, List<String> hashes, int indexLimit)
      throws OrmException, IOException {
    if (hashes.size() > indexLimit) {
      return byCommitsOnBranchNotMergedFromDatabase(repo, db, branch, hashes);
    } else {
      return byCommitsOnBranchNotMergedFromIndex(branch, hashes);
    }
  }

  private Iterable<ChangeData> byCommitsOnBranchNotMergedFromDatabase(
      Repository repo, ReviewDb db, Branch.NameKey branch, List<String> hashes)
      throws OrmException, IOException {
    Set<Change.Id> changeIds = Sets.newHashSetWithExpectedSize(hashes.size());
    String lastPrefix = null;
    for (Ref ref :
        repo.getRefDatabase().getRefs(RefNames.REFS_CHANGES).values()) {
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

    List<ChangeData> cds = new ArrayList<>(hashes.size());
    for (Change c : db.changes().get(changeIds)) {
      if (c.getDest().equals(branch) && c.getStatus() != Change.Status.MERGED) {
        cds.add(changeDataFactory.create(db, c));
      }
    }
    return cds;
  }

  private Iterable<ChangeData> byCommitsOnBranchNotMergedFromIndex(
      Branch.NameKey branch, List<String> hashes) throws OrmException {
    return query(and(
        ref(branch),
        project(branch.getParentKey()),
        not(status(Change.Status.MERGED)),
        or(commits(schema(indexes), hashes))));
  }

  private static List<Predicate<ChangeData>> commits(Schema<ChangeData> schema,
      List<String> hashes) {
    List<Predicate<ChangeData>> commits = new ArrayList<>(hashes.size());
    for (String s : hashes) {
      commits.add(commit(schema, s));
    }
    return commits;
  }

  public List<ChangeData> byProjectOpen(Project.NameKey project)
      throws OrmException {
    return query(and(project(project), open()));
  }

  public List<ChangeData> byTopicOpen(String topic)
      throws OrmException {
    return query(and(new ExactTopicPredicate(schema(indexes), topic), open()));
  }

  public List<ChangeData> byCommit(ObjectId id) throws OrmException {
    return query(commit(schema(indexes), id.name()));
  }

  public List<ChangeData> byProjectCommits(Project.NameKey project,
      List<String> hashes) throws OrmException {
    int n = indexConfig.maxTerms() - 1;
    checkArgument(hashes.size() <= n, "cannot exceed %s commits", n);
    return query(and(project(project), or(commits(schema(indexes), hashes))));
  }

  public List<ChangeData> bySubmissionId(String cs) throws OrmException {
    if (Strings.isNullOrEmpty(cs) || !schema(indexes).hasField(SUBMISSIONID)) {
      return Collections.emptyList();
    } else {
      return query(new SubmissionIdPredicate(cs));
    }
  }

  public List<ChangeData> byProjectGroups(Project.NameKey project,
      Collection<String> groups) throws OrmException {
    List<GroupPredicate> groupPredicates = new ArrayList<>(groups.size());
    for (String g : groups) {
      groupPredicates.add(new GroupPredicate(g));
    }
    return query(and(project(project), or(groupPredicates)));
  }

  private List<ChangeData> query(Predicate<ChangeData> p) throws OrmException {
    try {
      return qp.queryChanges(p).changes();
    } catch (QueryParseException e) {
      throw new OrmException(e);
    }
  }

  private static Schema<ChangeData> schema(@Nullable IndexCollection indexes) {
    ChangeIndex index = indexes != null ? indexes.getSearchIndex() : null;
    return index != null ? index.getSchema() : null;
  }
}
