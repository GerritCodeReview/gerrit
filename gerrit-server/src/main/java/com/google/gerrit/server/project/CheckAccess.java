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

package com.google.gerrit.server.project;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CheckAccess implements RestModifyView<ProjectResource, AccessCheckInput> {
  private final AccountResolver accountResolver;
  private final Provider<ReviewDb> db;
  private final IdentifiedUser.GenericFactory userFactory;
  private final PermissionBackend permissionBackend;

  @Inject
  CheckAccess(
      AccountResolver resolver,
      Provider<ReviewDb> db,
      IdentifiedUser.GenericFactory userFactory,
      PermissionBackend permissionBackend) {
    this.accountResolver = resolver;
    this.db = db;
    this.userFactory = userFactory;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public AccessCheckInfo apply(ProjectResource rsrc, AccessCheckInput input)
      throws OrmException, PermissionBackendException, RestApiException, IOException,
          ConfigInvalidException {
    permissionBackend.user(rsrc.getUser()).check(GlobalPermission.ADMINISTRATE_SERVER);

    if (input == null) {
      throw new BadRequestException("input is required");
    }
    if (Strings.isNullOrEmpty(input.account)) {
      throw new BadRequestException("input requires 'account'");
    }

    Account match = accountResolver.find(db.get(), input.account);
    if (match == null) {
      throw new BadRequestException(String.format("cannot find account %s", input.account));
    }

    AccessCheckInfo info = new AccessCheckInfo();

    IdentifiedUser user = userFactory.create(match.getId());
    try {
      permissionBackend.user(user).project(rsrc.getNameKey()).check(ProjectPermission.ACCESS);
    } catch (AuthException | PermissionBackendException e) {
      info.message =
          String.format(
              "user %s (%s) cannot see project %s",
              user.getNameEmail(), user.getAccount().getId(), rsrc.getName());
      info.status = HttpServletResponse.SC_FORBIDDEN;
      return info;
    }

    if (!Strings.isNullOrEmpty(input.ref)) {
      try {
        permissionBackend
            .user(user)
            .ref(new Branch.NameKey(rsrc.getNameKey(), input.ref))
            .check(RefPermission.READ);
      } catch (AuthException | PermissionBackendException e) {
        info.status = HttpServletResponse.SC_FORBIDDEN;
        info.message =
            String.format(
                "user %s (%s) cannot see ref %s in project %s",
                user.getNameEmail(), user.getAccount().getId(), input.ref, rsrc.getName());
        return info;
      }
    }

    info.status = HttpServletResponse.SC_OK;
    return info;
  }
}
