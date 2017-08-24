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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class BranchesCollection
    implements ChildCollection<ProjectResource, BranchResource>, AcceptsCreate<ProjectResource> {
  private final DynamicMap<RestView<BranchResource>> views;
  private final Provider<ListBranches> list;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final GitRepositoryManager repoManager;
  private final CreateBranch.Factory createBranchFactory;

  @Inject
  BranchesCollection(
      DynamicMap<RestView<BranchResource>> views,
      Provider<ListBranches> list,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      GitRepositoryManager repoManager,
      CreateBranch.Factory createBranchFactory) {
    this.views = views;
    this.list = list;
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.repoManager = repoManager;
    this.createBranchFactory = createBranchFactory;
  }

  @Override
  public RestView<ProjectResource> list() {
    return list.get();
  }

  @Override
  public BranchResource parse(ProjectResource parent, IdString id)
      throws ResourceNotFoundException, IOException, PermissionBackendException {
    Project.NameKey project = parent.getNameKey();
    try (Repository repo = repoManager.openRepository(project)) {
      Ref ref = repo.exactRef(RefNames.fullName(id.get()));
      if (ref == null) {
        throw new ResourceNotFoundException(id);
      }

      // ListBranches checks the target of a symbolic reference to determine access
      // rights on the symbolic reference itself. This check prevents seeing a hidden
      // branch simply because the symbolic reference name was visible.
      permissionBackend
          .user(user)
          .project(project)
          .ref(ref.isSymbolic() ? ref.getTarget().getName() : ref.getName())
          .check(RefPermission.READ);
      return new BranchResource(parent.getControl(), ref);
    } catch (AuthException notAllowed) {
      throw new ResourceNotFoundException(id);
    } catch (RepositoryNotFoundException noRepo) {
      throw new ResourceNotFoundException();
    }
  }

  @Override
  public DynamicMap<RestView<BranchResource>> views() {
    return views;
  }

  @SuppressWarnings("unchecked")
  @Override
  public CreateBranch create(ProjectResource parent, IdString name) {
    return createBranchFactory.create(name.get());
  }
}
