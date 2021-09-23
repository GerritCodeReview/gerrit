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

package com.google.gerrit.server.restapi.project;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.Reachable;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangePredicates;
import com.google.gerrit.server.update.RetryHelper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** The collection of commit IDs (ie. 40 char hex IDs) */
@Singleton
public class CommitsCollection implements ChildCollection<ProjectResource, CommitResource> {
  private final DynamicMap<RestView<CommitResource>> views;
  private final GitRepositoryManager repoManager;
  private final RetryHelper retryHelper;
  private final ChangeIndexCollection indexes;
  private final Reachable reachable;

  @Inject
  public CommitsCollection(
      DynamicMap<RestView<CommitResource>> views,
      GitRepositoryManager repoManager,
      RetryHelper retryHelper,
      ChangeIndexCollection indexes,
      Reachable reachable) {
    this.views = views;
    this.repoManager = repoManager;
    this.retryHelper = retryHelper;
    this.indexes = indexes;
    this.reachable = reachable;
  }

  @Override
  public RestView<ProjectResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public CommitResource parse(ProjectResource parent, IdString id)
      throws RestApiException, IOException {
    parent.getProjectState().checkStatePermitsRead();
    ObjectId objectId;
    try {
      objectId = ObjectId.fromString(id.get());
    } catch (IllegalArgumentException e) {
      throw new ResourceNotFoundException(id, e);
    }

    try (Repository repo = repoManager.openRepository(parent.getNameKey());
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(objectId);
      if (!canRead(parent.getProjectState(), repo, commit)) {
        throw new ResourceNotFoundException(id);
      }
      // GetCommit depends on the body of both the commit and parent being parsed, to get the
      // subject.
      rw.parseBody(commit);
      for (int i = 0; i < commit.getParentCount(); i++) {
        rw.parseBody(rw.parseCommit(commit.getParent(i)));
      }
      return new CommitResource(parent, commit);
    } catch (MissingObjectException | IncorrectObjectTypeException e) {
      throw new ResourceNotFoundException(id, e);
    }
  }

  @Override
  public DynamicMap<RestView<CommitResource>> views() {
    return views;
  }

  /**
   * Returns true if {@code commit} is visible to the caller and {@code commit} is reachable from
   * the given branch.
   */
  public boolean canRead(ProjectState state, Repository repo, RevCommit commit, Ref ref) {
    return reachable.fromRefs(state.getNameKey(), repo, commit, ImmutableList.of(ref));
  }

  /** Returns true if {@code commit} is visible to the caller. */
  public boolean canRead(ProjectState state, Repository repo, RevCommit commit) throws IOException {
    Project.NameKey project = state.getNameKey();
    if (indexes.getSearchIndex() == null) {
      // No index in slaves, fall back to scanning refs. We must inspect change refs too
      // as the commit might be a patchset of a not yet submitted change.
      return reachable.fromRefs(project, repo, commit, repo.getRefDatabase().getRefs());
    }

    // Check first if any patchset of any change references the commit in question. This is much
    // cheaper than ref visibility filtering and reachability computation.
    List<ChangeData> changes =
        retryHelper
            .changeIndexQuery(
                "queryChangesByProjectCommitWithLimit1",
                q -> q.enforceVisibility(true).setLimit(1).byProjectCommit(project, commit))
            .call();
    if (!changes.isEmpty()) {
      return true;
    }

    // Maybe the commit was a merge commit of a change. Try to find promising candidates for
    // branches to check, by seeing if its parents were associated to changes.
    Predicate<ChangeData> pred =
        Predicate.and(
            ChangePredicates.project(project),
            Predicate.or(
                Arrays.stream(commit.getParents())
                    .map(parent -> ChangePredicates.commitPrefix(parent.getId().getName()))
                    .collect(toImmutableList())));
    changes =
        retryHelper
            .changeIndexQuery(
                "queryChangesByProjectCommit", q -> q.enforceVisibility(true).query(pred))
            .call();

    Set<Ref> branchesForCommitParents = new HashSet<>(changes.size());
    for (ChangeData cd : changes) {
      Ref ref = repo.exactRef(cd.change().getDest().branch());
      if (ref != null) {
        branchesForCommitParents.add(ref);
      }
    }

    if (reachable.fromRefs(
        project, repo, commit, branchesForCommitParents.stream().collect(Collectors.toList()))) {
      return true;
    }

    // If we have already checked change refs using the change index, spare any further checks for
    // changes.
    List<Ref> refs =
        repo.getRefDatabase()
            .getRefsByPrefixWithExclusions(RefDatabase.ALL, ImmutableSet.of(RefNames.REFS_CHANGES));
    return reachable.fromRefs(project, repo, commit, refs);
  }
}
