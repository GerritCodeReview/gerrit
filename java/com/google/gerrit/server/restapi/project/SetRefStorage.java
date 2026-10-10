// Copyright (C) 2026 The Android Open Source Project
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

import com.google.gerrit.extensions.api.projects.RefStorageInput;
import com.google.gerrit.extensions.client.RefStorage;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.DelegateRepository;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefStorageFormat;
import org.eclipse.jgit.lib.Repository;

/**
 * Converts a project's reference storage between {@code RefDirectory} and reftable.
 *
 * <p>Conversion is supported in both directions, but only for a local repository that is not
 * already in the requested format and is not served with {@code core.usePerRequestRefCache=true}.
 */
@Singleton
public class SetRefStorage implements RestModifyView<ProjectResource, RefStorageInput> {
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final boolean usePerRequestRefCache;

  @Inject
  SetRefStorage(
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      @GerritServerConfig Config cfg) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.usePerRequestRefCache = cfg.getBoolean("core", null, "usePerRequestRefCache", true);
  }

  @Override
  public Response<String> apply(ProjectResource rsrc, RefStorageInput input)
      throws AuthException,
          BadRequestException,
          ResourceConflictException,
          ResourceNotFoundException,
          IOException,
          PermissionBackendException {
    permissionBackend.user(rsrc.getUser()).check(GlobalPermission.ADMINISTRATE_SERVER);

    if (input == null || input.refStorage == null) {
      throw new BadRequestException("ref_storage is required");
    }
    RefStorage target = input.refStorage;
    boolean backup = input.backup == null || input.backup;

    // Reftable is unservable while core.usePerRequestRefCache is enabled (the per-request cache
    // assumes a RefDirectory), so refuse converting to it in that case.
    if (target == RefStorage.REFTABLE && usePerRequestRefCache) {
      throw new ResourceConflictException(
          "Converting to reftable requires core.usePerRequestRefCache=false");
    }

    try (Repository repo = repoManager.openRepository(rsrc.getNameKey())) {
      Repository real =
          repo instanceof DelegateRepository ? ((DelegateRepository) repo).delegate() : repo;
      if (!(real instanceof FileRepository)) {
        throw new ResourceConflictException(
            "Ref storage cannot be converted; it is not a local repository");
      }
      RefDatabase refDb = real.getRefDatabase();
      RefStorage current = ConfigInfoCreator.refStorageOf(refDb);
      if (current == target) {
        throw new ResourceConflictException(
            "Repository is already in " + target.name() + " ref storage format");
      }
      RefStorageFormat format =
          target == RefStorage.REFTABLE ? RefStorageFormat.REFTABLE : RefStorageFormat.FILES;
      ((FileRepository) real).convertRefStorage(format, /* writeLogs= */ true, backup);
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(rsrc.getName(), e);
    }
    return Response.ok(target.name());
  }
}
