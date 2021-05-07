// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to mark an account as inactive.
 *
 * <p>This REST endpoint handles {@code DELETE /accounts/<account-identifier>/active} requests.
 *
 * <p>Inactive accounts cannot login into Gerrit.
 *
 * <p>Marking an account as active is handled by {@link PutActive}.
 */
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class DeleteAccount implements RestModifyView<AccountResource, Input> {

  private final Provider<IdentifiedUser> self;
  private final Provider<DeleteActive> deleteActive;
  private final Provider<DeleteExternalIds> deleteExternalIds;
  private final ExternalIds externalIds;

  @Inject
  DeleteAccount(
      Provider<IdentifiedUser> self,
      Provider<DeleteActive> deleteActive,
      Provider<DeleteExternalIds> deleteExternalIds,
      ExternalIds externalIds) {
    this.self = self;
    this.deleteActive = deleteActive;
    this.deleteExternalIds = deleteExternalIds;
    this.externalIds = externalIds;
  }

  @Override
  public Response<?> apply(AccountResource rsrc, Input input)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    if (self.get().hasSameAccountId(rsrc.getUser())) {
      throw new ResourceConflictException("cannot delete own account");
    }

    deleteActive.get().apply(rsrc, input);

    List<String> ids =
        externalIds.byAccount(rsrc.getUser().getAccountId()).stream()
            .map(e -> e.key().get())
            .collect(Collectors.toList());
    if (ids.isEmpty()) {
      throw new ResourceNotFoundException("Account has no external Ids");
    }
    deleteExternalIds.get().apply(rsrc, ids);
    return Response.none();
  }
}
