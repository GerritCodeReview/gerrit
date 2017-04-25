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

package com.google.gerrit.server.config;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInfo.Result;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class CheckAccess implements RestModifyView<ConfigResource, AccessCheckInput> {
  private final PermissionBackend permissionBackend;
  private final AccountResolver resolver;
  private final Provider<IdentifiedUser> currentUser;
  private final Provider<ReviewDb> db;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ProjectControl.GenericFactory projectFactory;
  private final ProjectCache projectCache;

  @Inject
  CheckAccess(Provider<IdentifiedUser> currentUser,
      AccountResolver resolver,
      Provider<ReviewDb> db,
      IdentifiedUser.GenericFactory userFactory,
      ProjectControl.GenericFactory projectFactory,
      PermissionBackend permissionBackend,
      ProjectCache projectCache
  ) {
    this.currentUser = currentUser;
    this.resolver = resolver;
    this.db = db;
    this.userFactory = userFactory;
    this.projectFactory = projectFactory;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
  }

  @Override
  public AccessCheckInfo apply(ConfigResource unused, AccessCheckInput input)
      throws RestApiException, IOException {
    IdentifiedUser admin = currentUser.get();
    if (!admin.isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    try {
      permissionBackend.user(currentUser.get()).check(GlobalPermission.ADMINISTRATE_SERVER);
    } catch (PermissionBackendException e) {
      throw new AuthException("You are not allowed to run access checks");
    }

    if (input == null) {
      throw new BadRequestException("input is required");
    }
    if (input.account == null) {
      throw new BadRequestException("must set account in input");
    }
    if (input.project == null) {
      throw new BadRequestException("must set project in input");
    }

    Account match = null;
    try {
      match = resolver.find(db.get(), input.account);
    } catch (OrmException e) {
      throw new IOException(e);
    }
    if (match == null) {
      throw new BadRequestException(String.format("cannot find account %s", input.account));
    }

    IdentifiedUser user = userFactory.create(match.getId());
    AccessCheckInfo info = new AccessCheckInfo();
    info.result = new Result();

    Project.NameKey key = new NameKey(input.project);
    if (projectCache.get(key) == null) {
      info.result.message = String.format("project %s does not exist", key);
      info.result.status = 404; // TODO - constant
      return info;
    }

    ProjectControl projectControl;
    try {
      projectControl = projectFactory.validateFor(key, ProjectControl.VISIBLE, user);
    } catch (NoSuchProjectException e) {
      info.result.message = String.format("user %s (%s) cannot see project %s",
          user.getNameEmail(), user.getAccount().getId(), key);
      info.result.status = 403; // TODO - constant
      return info;
    }

    if (!Strings.isNullOrEmpty(input.ref)) {
      RefControl refControl = projectControl.controlForRef(input.ref);
      if (!refControl.forUser(user).isVisible()) {
        info.result.status = 403;
        info.result.message = String.format("user %s (%s) cannot see ref %s in project %s",
            user.getNameEmail(), user.getAccount().getId(), input.ref, key);
        return info;
      }
    }

    info.result.status = 200; // TODO - constant
    return info;
  }
}
