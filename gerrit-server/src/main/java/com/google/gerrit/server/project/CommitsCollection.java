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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.IncludedInResolver;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CommitsCollection implements ChildCollection<ProjectResource, CommitResource> {
  private static final Logger log = LoggerFactory.getLogger(CommitsCollection.class);

  private final DynamicMap<RestView<CommitResource>> views;
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> user;
  private final VisibleRefFilter.Factory refFilter;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  public CommitsCollection(
      DynamicMap<RestView<CommitResource>> views,
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      Provider<ReviewDb> db,
      Provider<CurrentUser> user,
      VisibleRefFilter.Factory refFilter,
      Provider<InternalChangeQuery> queryProvider) {
    this.views = views;
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.db = db;
    this.user = user;
    this.refFilter = refFilter;
    this.queryProvider = queryProvider;
  }

  @Override
  public RestView<ProjectResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public CommitResource parse(ProjectResource parent, IdString id)
      throws ResourceNotFoundException, IOException {
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
  public boolean canRead(ProjectState state, Repository repo, RevCommit commit) {
    Project.NameKey project = state.getProject().getNameKey();

    // Look for changes associated with the commit.
    try {
      PermissionBackend.WithUser perm = permissionBackend.user(user).database(db);
      List<ChangeData> changes = queryProvider.get().byProjectCommit(project, commit);
      if (!perm.filterChangeData(ChangePermission.READ, changes).isEmpty()) {
        return true;
      }
    } catch (OrmException | PermissionBackendException e) {
      log.error("Cannot look up change for commit " + commit.name() + " in " + project, e);
    }

    return isReachableFrom(state, repo, commit, repo.getAllRefs());
  }

  public boolean isReachableFrom(
      ProjectState state, Repository repo, RevCommit commit, Map<String, Ref> refs) {
    try (RevWalk rw = new RevWalk(repo)) {
      refs = refFilter.create(state, repo).filter(refs, true);
      return !refs.isEmpty() && IncludedInResolver.includedInOne(repo, rw, commit, refs.values());
    } catch (IOException e) {
      log.error(String.format(
          "Cannot verify permissions to commit object %s in repository %s",
          commit.name(), state.getProject().getNameKey()), e);
      return false;
    }
  }
}
