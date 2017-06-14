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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static java.util.stream.Collectors.toMap;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteExternalIds implements RestModifyView<AccountResource, List<String>> {
  private final AccountManager accountManager;
  private final ExternalIds externalIds;
  private final Provider<CurrentUser> self;

  @Inject
  DeleteExternalIds(
      AccountManager accountManager, ExternalIds externalIds, Provider<CurrentUser> self) {
    this.accountManager = accountManager;
    this.externalIds = externalIds;
    this.self = self;
  }

  @Override
  public Response<?> apply(AccountResource resource, List<String> extIds)
      throws RestApiException, IOException, OrmException, ConfigInvalidException {
    if (self.get() != resource.getUser() && !self.get().getCapabilities().canAccessDatabase()) {
      throw new AuthException("not allowed to delete external IDs");
    }

    if (extIds == null || extIds.size() == 0) {
      throw new BadRequestException("external IDs are required");
    }

    Map<ExternalId.Key, ExternalId> externalIdMap =
        externalIds
            .byAccount(resource.getUser().getAccountId())
            .stream()
            .collect(toMap(i -> i.key(), i -> i));

    List<ExternalId> toDelete = new ArrayList<>();
    ExternalId.Key last = resource.getUser().getLastLoginExternalIdKey();
    for (String externalIdStr : extIds) {
      ExternalId id = externalIdMap.get(ExternalId.Key.parse(externalIdStr));

      if (id == null) {
        throw new UnprocessableEntityException(
            String.format("External id %s does not exist", externalIdStr));
      }

      if ((!id.isScheme(SCHEME_USERNAME))
          && ((last == null) || (!last.get().equals(id.key().get())))) {
        toDelete.add(id);
      } else {
        throw new ResourceConflictException(
            String.format("External id %s cannot be deleted", externalIdStr));
      }
    }

    try {
      for (ExternalId extId : toDelete) {
        AuthRequest authRequest = new AuthRequest(extId.key());
        authRequest.setEmailAddress(extId.email());
        accountManager.unlink(extId.accountId(), authRequest);
      }
    } catch (AccountException e) {
      throw new ResourceConflictException(e.getMessage());
    }

    return Response.none();
  }
}
