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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.Reachable;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class CommitsCollection implements ChildCollection<ProjectResource, CommitResource> {
  private final DynamicMap<RestView<CommitResource>> views;
  private final GitRepositoryManager repoManager;
  private final ChangeIndexCollection indexes;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Reachable reachable;

  @Inject
  public CommitsCollection(
      DynamicMap<RestView<CommitResource>> views,
      GitRepositoryManager repoManager,
      ChangeIndexCollection indexes,
      Provider<InternalChangeQuery> queryProvider,
      Reachable reachable) {
    this.views = views;
    this.repoManager = repoManager;
    this.indexes = indexes;
    this.queryProvider = queryProvider;
    this.reachable = reachable;
  }

  @Override
  public RestView<ProjectResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public CommitResource parse(ProjectResource parent, IdString id)
      throws RestApiException, IOException, StorageException {
    parent.getProjectState().checkStatePermitsRead();
    ObjectId objectId;
    try {
      objectId = ObjectId.fromString(id.get());
    } catch (IllegalArgumentException e) {
      throw new ResourceNotFoundException(id);
    }

    try (Repository repo = repoManager.openRepository(parent.getNameKey());
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(objectId);
      rw.parseBody(commit);
      if (!canRead(parent.getProjectState(), repo, commit)) {
        throw new ResourceNotFoundException(id);
      }
      for (int i = 0; i < commit.getParentCount(); i++) {
        rw.parseBody(rw.parseCommit(commit.getParent(i)));
      }
      return new CommitResource(parent, commit);
    } catch (MissingObjectException | IncorrectObjectTypeException e) {
      throw new ResourceNotFoundException(id);
    }
  }

  @Override
  public DynamicMap<RestView<CommitResource>> views() {
    return views;
  }

  /** @return true if {@code commit} is visible to the caller. */
  public boolean canRead(ProjectState state, Repository repo, RevCommit commit)
      throws StorageException, IOException {
    Project.NameKey project = state.getNameKey();
    if (indexes.getSearchIndex() == null) {
      // No index in slaves, fall back to scanning refs.
      return reachable.fromRefs(project, repo, commit, repo.getRefDatabase().getRefs());
    }

    // Check first if any change references the commit in question. This is much cheaper than ref
    // visibility filtering and reachability computation.
    List<ChangeData> changes =
        queryProvider.get().enforceVisibility(true).setLimit(1).byProjectCommit(project, commit);
    if (!changes.isEmpty()) {
      return true;
    }

    // If we have already checked change refs using the change index, spare any further checks for
    // changes.
    List<Ref> refs =
        repo.getRefDatabase().getRefs().stream()
            .filter(r -> !r.getName().startsWith(RefNames.REFS_CHANGES))
            .collect(toImmutableList());
    return reachable.fromRefs(project, repo, commit, refs);
  }
}
