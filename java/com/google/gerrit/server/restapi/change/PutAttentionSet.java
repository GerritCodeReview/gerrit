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
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.AddToAttentionSetOp;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Adds a single user to the attention set. */
public class PutAttentionSet implements RestModifyView<ChangeResource, AttentionSetInput> {
  private final BatchUpdate.Factory updateFactory;
  private final AccountResolver accountResolver;
  private final AddToAttentionSetOp.Factory opFactory;
  private final AccountLoader.Factory accountLoaderFactory;
  private final PermissionBackend permissionBackend;
  private final ApprovalsUtil approvalsUtil;

  @Inject
  PutAttentionSet( // ö prune args
      BatchUpdate.Factory updateFactory,
      AccountResolver accountResolver,
      AddToAttentionSetOp.Factory opFactory,
      AccountLoader.Factory accountLoaderFactory,
      PermissionBackend permissionBackend,
      ApprovalsUtil approvalsUtil) {
    this.updateFactory = updateFactory;
    this.accountResolver = accountResolver;
    this.opFactory = opFactory;
    this.accountLoaderFactory = accountLoaderFactory;
    this.permissionBackend = permissionBackend;
    this.approvalsUtil = approvalsUtil;
  }

  @Override
  public Response<?> apply(ChangeResource resource, AttentionSetInput input)
      throws RestApiException, PermissionBackendException, IOException, ConfigInvalidException,
          UpdateException {
    // ö Add dedicated permission.
    resource.permissions().check(ChangePermission.EDIT_ASSIGNEE);

    input.user = Strings.nullToEmpty(input.user).trim();
    if (input.user.isEmpty()) {
      throw new BadRequestException("missing user field");
    }

    IdentifiedUser attentionUser = accountResolver.resolve(input.user).asUniqueUser();
    try {
      permissionBackend
          .absentUser(attentionUser.getAccountId())
          .change(resource.getNotes())
          .check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new AuthException("read not permitted for " + input.user, e);
    }

    try (BatchUpdate bu =
        updateFactory.create(
            resource.getChange().getProject(), resource.getUser(), TimeUtil.nowTs())) {
      AddToAttentionSetOp op = opFactory.create(attentionUser, input.reason);
      bu.addOp(resource.getId(), op);

      ReviewerSet currentReviewers = approvalsUtil.getReviewers(resource.getNotes());
      // ö Add better invariants.
      if (!currentReviewers.all().contains(attentionUser.getAccountId())) {
        throw new BadRequestException("user " + input.user + " must be a reviewer");
      }

      bu.execute();
      return Response.ok(accountLoaderFactory.create(true).fillOne(attentionUser.getAccountId()));
    }
  }
}
