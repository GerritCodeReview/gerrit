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

import static com.google.gerrit.server.query.Predicate.and;
import static com.google.gerrit.server.query.Predicate.or;
import static com.google.gerrit.server.query.change.ChangeStatusPredicate.open;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

  private static Predicate<ChangeData> commit(AbbreviatedObjectId id) {
    return new CommitPredicate(id);
  }

  private final QueryProcessor qp;
  private final IndexCollection indexes;

  @Inject
  InternalChangeQuery(QueryProcessor queryProcessor,
      IndexCollection indexes) {
    qp = queryProcessor.enforceVisibility(false);
    this.indexes = indexes;
  }

  public InternalChangeQuery setLimit(int n) {
    qp.setLimit(n);
    return this;
  }

  public InternalChangeQuery enforceVisibility(boolean enforce) {
    qp.enforceVisibility(enforce);
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

  public List<ChangeData> submitted(Branch.NameKey branch) throws OrmException {
    return query(and(
        ref(branch),
        project(branch.getParentKey()),
        status(Change.Status.SUBMITTED)));
  }

  public List<ChangeData> allSubmitted() throws OrmException {
    return query(status(Change.Status.SUBMITTED));
  }

  public List<ChangeData> byBranchOpen(Branch.NameKey branch)
      throws OrmException {
    return query(and(
        ref(branch),
        project(branch.getParentKey()),
        open()));
  }

  public List<ChangeData> byProjectOpen(Project.NameKey project)
      throws OrmException {
    return query(and(project(project), open()));
  }

  public List<ChangeData> byExactTopicOpen(String topic)
      throws OrmException {
    return query(and(new ExactTopicPredicate(schema(indexes), topic), open()));
  }

  public List<ChangeData> byCommitPrefix(String prefix) throws OrmException {
    return query(commit(AbbreviatedObjectId.fromString(prefix)));
  }

  public List<ChangeData> byCommit(ObjectId id) throws OrmException {
    return query(commit(AbbreviatedObjectId.fromObjectId(id)));
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
