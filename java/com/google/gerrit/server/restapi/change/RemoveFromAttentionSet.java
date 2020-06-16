// Copyright (C) 2020 The Android Open Source Project
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
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.AttentionSetEntryResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RemoveFromAttentionSetOp;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Removes a single user from the attention set. */
public class RemoveFromAttentionSet
    implements RestModifyView<AttentionSetEntryResource, AttentionSetInput> {
  private final BatchUpdate.Factory updateFactory;
  private final RemoveFromAttentionSetOp.Factory opFactory;
  private final AccountResolver accountResolver;

  @Inject
  RemoveFromAttentionSet(
      BatchUpdate.Factory updateFactory,
      RemoveFromAttentionSetOp.Factory opFactory,
      AccountResolver accountResolver) {
    this.updateFactory = updateFactory;
    this.opFactory = opFactory;
    this.accountResolver = accountResolver;
  }

  @Override
  public Response<Object> apply(
      AttentionSetEntryResource attentionResource, AttentionSetInput input)
      throws RestApiException, PermissionBackendException, IOException, ConfigInvalidException,
          UpdateException {
    if (input == null) {
      throw new BadRequestException("input may not be null");
    }
    input.reason = Strings.nullToEmpty(input.reason).trim();
    if (input.reason.isEmpty()) {
      throw new BadRequestException("missing field: reason");
    }
    input.user = Strings.nullToEmpty(input.user).trim();
    if (!input.user.isEmpty()) {
      Account.Id attentionUserId = null;
      try {
        attentionUserId = accountResolver.resolve(input.user).asUnique().account().id();
      } catch (AccountResolver.UnresolvableAccountException ex) {
        // Do nothing here, and then just throw BadRequestException.
      }
      if (attentionUserId == null
          || attentionUserId.get() != attentionResource.getAccountId().get()) {
        throw new BadRequestException(
            "The field \"user\" should be empty, or not conflict with the user in the request.");
      }
    }
    ChangeResource changeResource = attentionResource.getChangeResource();
    try (BatchUpdate bu =
        updateFactory.create(
            changeResource.getProject(), changeResource.getUser(), TimeUtil.nowTs())) {
      RemoveFromAttentionSetOp op =
          opFactory.create(attentionResource.getAccountId(), input.reason, true);
      bu.addOp(changeResource.getId(), op);
      bu.execute();
    }
    return Response.none();
  }
}
