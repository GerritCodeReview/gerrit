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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.change.AddToAttentionSetOp;
import com.google.gerrit.server.change.AttentionSetEntryResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Adds a single user to the attention set. */
@Singleton
public class AddToAttentionSet
    implements RestCollectionModifyView<
        ChangeResource, AttentionSetEntryResource, AttentionSetInput> {

  private final BatchUpdate.Factory updateFactory;
  private final AccountResolver accountResolver;
  private final AddToAttentionSetOp.Factory opFactory;
  private final AccountLoader.Factory accountLoaderFactory;
  private final PermissionBackend permissionBackend;
  private final NotifyResolver notifyResolver;
  private final ServiceUserClassifier serviceUserClassifier;

  @Inject
  AddToAttentionSet(
      BatchUpdate.Factory updateFactory,
      AccountResolver accountResolver,
      AddToAttentionSetOp.Factory opFactory,
      AccountLoader.Factory accountLoaderFactory,
      PermissionBackend permissionBackend,
      NotifyResolver notifyResolver,
      ServiceUserClassifier serviceUserClassifier) {
    this.updateFactory = updateFactory;
    this.accountResolver = accountResolver;
    this.opFactory = opFactory;
    this.accountLoaderFactory = accountLoaderFactory;
    this.permissionBackend = permissionBackend;
    this.notifyResolver = notifyResolver;
    this.serviceUserClassifier = serviceUserClassifier;
  }

  @Override
  public Response<AccountInfo> apply(ChangeResource changeResource, AttentionSetInput input)
      throws Exception {
    AttentionSetUtil.validateInput(input);
    Account.Id attentionUserId =
        AttentionSetUtil.resolveAccount(accountResolver, changeResource.getNotes(), input.user);

    if (serviceUserClassifier.isServiceUser(attentionUserId)) {
      throw new BadRequestException(
          String.format(
              "%s is a robot, and robots can't be added to the attention set.", input.user));
    }
    if (!permissionBackend
        .absentUser(attentionUserId)
        .change(changeResource.getNotes())
        .test(ChangePermission.READ)) {
      throw new AuthException("read not permitted for " + attentionUserId);
    }
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate bu =
          updateFactory.create(
              changeResource.getChange().getProject(), changeResource.getUser(), TimeUtil.now())) {
        AddToAttentionSetOp op = opFactory.create(attentionUserId, input.reason, true);
        bu.addOp(changeResource.getId(), op);
        NotifyHandling notify = input.notify == null ? NotifyHandling.OWNER : input.notify;
        NotifyResolver.Result notifyResult = notifyResolver.resolve(notify, input.notifyDetails);
        bu.setNotify(notifyResult);
        bu.execute();
        return Response.ok(accountLoaderFactory.create(true).fillOne(attentionUserId));
      }
    }
  }
}
