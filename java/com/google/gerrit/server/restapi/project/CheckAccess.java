// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.entities.RefNames.REFS_HEADS;

import com.google.common.base.Strings;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.DefaultPermissionMappings;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class CheckAccess implements RestModifyView<ProjectResource, AccessCheckInput> {
  private final AccountResolver accountResolver;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager gitRepositoryManager;

  @Inject
  CheckAccess(
      AccountResolver resolver,
      PermissionBackend permissionBackend,
      GitRepositoryManager gitRepositoryManager) {
    this.accountResolver = resolver;
    this.permissionBackend = permissionBackend;
    this.gitRepositoryManager = gitRepositoryManager;
  }

  @Override
  public Response<AccessCheckInfo> apply(ProjectResource rsrc, AccessCheckInput input)
      throws PermissionBackendException, RestApiException, IOException, ConfigInvalidException {
    permissionBackend.user(rsrc.getUser()).check(GlobalPermission.VIEW_ACCESS);

    rsrc.getProjectState().checkStatePermitsRead();

    if (input == null) {
      throw new BadRequestException("input is required");
    }
    if (Strings.isNullOrEmpty(input.account)) {
      throw new BadRequestException("input requires 'account'");
    }

    Account.Id match = accountResolver.resolve(input.account).asUnique().account().id();

    AccessCheckInfo info = new AccessCheckInfo();
    try {
      permissionBackend
          .absentUser(match)
          .project(rsrc.getNameKey())
          .check(ProjectPermission.ACCESS);
    } catch (AuthException e) {
      info.message = String.format("user %s cannot see project %s", match, rsrc.getName());
      info.status = HttpServletResponse.SC_FORBIDDEN;
      return Response.ok(info);
    }

    RefPermission refPerm;
    if (!Strings.isNullOrEmpty(input.permission)) {
      if (Strings.isNullOrEmpty(input.ref)) {
        throw new BadRequestException("must set 'ref' when specifying 'permission'");
      }
      Optional<RefPermission> rp = DefaultPermissionMappings.refPermission(input.permission);
      if (!rp.isPresent()) {
        throw new BadRequestException(
            String.format("'%s' is not recognized as ref permission", input.permission));
      }

      refPerm = rp.get();
    } else {
      refPerm = RefPermission.READ;
    }

    if (!Strings.isNullOrEmpty(input.ref)) {
      try {
        permissionBackend
            .absentUser(match)
            .ref(BranchNameKey.create(rsrc.getNameKey(), input.ref))
            .check(refPerm);
      } catch (AuthException e) {
        info.status = HttpServletResponse.SC_FORBIDDEN;
        info.message =
            String.format(
                "user %s lacks permission %s for %s in project %s",
                match, input.permission, input.ref, rsrc.getName());
        return Response.ok(info);
      }
    } else {
      // We say access is okay if there are no refs, but this warrants a warning,
      // as access denied looks the same as no branches to the user.
      try (Repository repo = gitRepositoryManager.openRepository(rsrc.getNameKey())) {
        if (repo.getRefDatabase().getRefsByPrefix(REFS_HEADS).isEmpty()) {
          info.message = "access is OK, but repository has no branches under refs/heads/";
        }
      }
    }
    info.status = HttpServletResponse.SC_OK;
    return Response.ok(info);
  }
}
