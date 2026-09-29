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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.changes.ChangePermissionsInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.DefaultPermissionMappings;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

public class GetChangePermissions implements RestReadView<ChangeResource> {

  /** Change-scoped permissions surfaced by default through this endpoint. */
  private static final ImmutableSet<ChangePermission> EXPOSED_PERMISSIONS =
      Sets.immutableEnumSet(ChangePermission.AI_REVIEW);

  private final PermissionBackend permissionBackend;
  private final AccountResolver accountResolver;

  private boolean all;
  private String account;

  @Inject
  GetChangePermissions(PermissionBackend permissionBackend, AccountResolver accountResolver) {
    this.permissionBackend = permissionBackend;
    this.accountResolver = accountResolver;
  }

  @Option(name = "-o", usage = "the ALL option returns every granted change permission")
  void addOption(String option) {
    if ("ALL".equalsIgnoreCase(option)) {
      all = true;
      return;
    }
    throw new IllegalArgumentException("option not recognized: " + option);
  }

  public void setAll(boolean all) {
    this.all = all;
  }

  @Option(
      name = "--account",
      usage = "report permissions for this account instead of the caller, requires admin")
  public void setAccount(String account) {
    this.account = account;
  }

  @Override
  public Response<ChangePermissionsInfo> apply(ChangeResource rsrc)
      throws PermissionBackendException, RestApiException, IOException, ConfigInvalidException {
    PermissionBackend.ForChange forChange = forChange(rsrc);
    Set<ChangePermission> candidates =
        all ? EnumSet.allOf(ChangePermission.class) : EXPOSED_PERMISSIONS;
    ChangePermissionsInfo info = new ChangePermissionsInfo();
    for (ChangePermission permission : candidates) {
      if (forChange.testOrFalse(permission)) {
        info.permissions.add(
            DefaultPermissionMappings.changePermissionName(permission)
                .orElseThrow(
                    () -> new IllegalStateException("no permission name for " + permission)));
      }
    }
    return Response.ok(info);
  }

  private PermissionBackend.ForChange forChange(ChangeResource rsrc)
      throws PermissionBackendException, RestApiException, IOException, ConfigInvalidException {
    if (Strings.isNullOrEmpty(account)) {
      return rsrc.permissions();
    }
    permissionBackend.user(rsrc.getUser()).check(GlobalPermission.ADMINISTRATE_SERVER);
    Account.Id target = accountResolver.resolve(account).asUnique().account().id();
    return permissionBackend.absentUser(target).change(rsrc.getNotes());
  }
}
