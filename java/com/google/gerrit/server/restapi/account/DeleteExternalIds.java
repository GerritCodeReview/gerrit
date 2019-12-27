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

package com.google.gerrit.server.restapi.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to delete external IDs from an account.
 *
 * <p>This REST endpoint handles {@code POST /accounts/<account-identifier>/external.ids:delete}
 * requests.
 */
@Singleton
public class DeleteExternalIds implements RestModifyView<AccountResource, List<String>> {
  private final PermissionBackend permissionBackend;
  private final AccountManager accountManager;
  private final ExternalIds externalIds;
  private final Provider<CurrentUser> self;

  @Inject
  DeleteExternalIds(
      PermissionBackend permissionBackend,
      AccountManager accountManager,
      ExternalIds externalIds,
      Provider<CurrentUser> self) {
    this.permissionBackend = permissionBackend;
    this.accountManager = accountManager;
    this.externalIds = externalIds;
    this.self = self;
  }

  @Override
  public Response<?> apply(AccountResource resource, List<String> extIds)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    if (!self.get().hasSameAccountId(resource.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.ACCESS_DATABASE);
    }

    if (extIds == null || extIds.size() == 0) {
      throw new BadRequestException("external IDs are required");
    }

    Map<ExternalId.Key, ExternalId> externalIdMap =
        externalIds.byAccount(resource.getUser().getAccountId()).stream()
            .collect(toMap(ExternalId::key, Function.identity()));

    List<ExternalId> toDelete = new ArrayList<>();
    Optional<ExternalId.Key> last = resource.getUser().getLastLoginExternalIdKey();
    for (String externalIdStr : extIds) {
      ExternalId id = externalIdMap.get(ExternalId.Key.parse(externalIdStr));

      if (id == null) {
        throw new UnprocessableEntityException(
            String.format("External id %s does not exist", externalIdStr));
      }

      if ((!id.isScheme(SCHEME_USERNAME))
          && (!last.isPresent() || (!last.get().equals(id.key())))) {
        toDelete.add(id);
      } else {
        throw new ResourceConflictException(
            String.format("External id %s cannot be deleted", externalIdStr));
      }
    }

    try {
      accountManager.unlink(
          resource.getUser().getAccountId(),
          toDelete.stream().map(ExternalId::key).collect(toSet()));
    } catch (AccountException e) {
      throw new ResourceConflictException(e.getMessage());
    }

    return Response.none();
  }
}
