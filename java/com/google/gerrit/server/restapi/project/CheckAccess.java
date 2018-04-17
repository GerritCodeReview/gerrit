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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_HEADS;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.DefaultPermissionMappings;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gwtorm.server.OrmException;
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
  private final IdentifiedUser.GenericFactory userFactory;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager gitRepositoryManager;

  @Inject
  CheckAccess(
      AccountResolver resolver,
      IdentifiedUser.GenericFactory userFactory,
      PermissionBackend permissionBackend,
      GitRepositoryManager gitRepositoryManager) {
    this.accountResolver = resolver;
    this.userFactory = userFactory;
    this.permissionBackend = permissionBackend;
    this.gitRepositoryManager = gitRepositoryManager;
  }

  @Override
  public AccessCheckInfo apply(ProjectResource rsrc, AccessCheckInput input)
      throws OrmException, PermissionBackendException, RestApiException, IOException,
          ConfigInvalidException {
    permissionBackend.user(rsrc.getUser()).check(GlobalPermission.VIEW_ACCESS);

    rsrc.getProjectState().checkStatePermitsRead();

    if (input == null) {
      throw new BadRequestException("input is required");
    }
    if (Strings.isNullOrEmpty(input.account)) {
      throw new BadRequestException("input requires 'account'");
    }

    Account match = accountResolver.find(input.account);
    if (match == null) {
      throw new UnprocessableEntityException(
          String.format("cannot find account %s", input.account));
    }

    AccessCheckInfo info = new AccessCheckInfo();

    IdentifiedUser user = userFactory.create(match.getId());
    try {
      permissionBackend.user(user).project(rsrc.getNameKey()).check(ProjectPermission.ACCESS);
    } catch (AuthException e) {
      info.message =
          String.format(
              "user %s (%s) cannot see project %s",
              user.getNameEmail(), user.getAccount().getId(), rsrc.getName());
      info.status = HttpServletResponse.SC_FORBIDDEN;
      return info;
    }

    RefPermission refPerm = null;
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
            .user(user)
            .ref(new Branch.NameKey(rsrc.getNameKey(), input.ref))
            .check(refPerm);
      } catch (AuthException e) {
        info.status = HttpServletResponse.SC_FORBIDDEN;
        info.message =
            String.format(
                "user %s (%s) lacks permission %s for %s in project %s",
                user.getNameEmail(),
                user.getAccount().getId(),
                input.permission,
                input.ref,
                rsrc.getName());
        return info;
      }
    } else {
      // We say access is okay if there are no refs, but this warrants a warning,
      // as access denied looks the same as no branches to the user.
      try (Repository repo = gitRepositoryManager.openRepository(rsrc.getNameKey())) {
        if (repo.getRefDatabase().getRefs(REFS_HEADS).isEmpty()) {
          info.message = "access is OK, but repository has no branches under refs/heads/";
        }
      }
    }
    info.status = HttpServletResponse.SC_OK;
    return info;
  }
}
