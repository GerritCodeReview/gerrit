// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.gerrit.server.ChangeMessagesUtil.createChangeMessageInfo;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.DeleteChangeMessageInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ChangeMessageResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;

/** Deletes a change message by rewriting history. */
@Singleton
public class DeleteChangeMessage
    extends RetryingRestModifyView<
        ChangeMessageResource, DeleteChangeMessageInput, Response<ChangeMessageInfo>> {

  private final Provider<CurrentUser> userProvider;
  private final PermissionBackend permissionBackend;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final AccountLoader.Factory accountLoaderFactory;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  public DeleteChangeMessage(
      Provider<CurrentUser> userProvider,
      PermissionBackend permissionBackend,
      ChangeMessagesUtil changeMessagesUtil,
      AccountLoader.Factory accountLoaderFactory,
      ChangeNotes.Factory notesFactory,
      RetryHelper retryHelper) {
    super(retryHelper);
    this.userProvider = userProvider;
    this.permissionBackend = permissionBackend;
    this.changeMessagesUtil = changeMessagesUtil;
    this.accountLoaderFactory = accountLoaderFactory;
    this.notesFactory = notesFactory;
  }

  @Override
  public Response<ChangeMessageInfo> applyImpl(
      BatchUpdate.Factory updateFactory,
      ChangeMessageResource resource,
      DeleteChangeMessageInput input)
      throws RestApiException, PermissionBackendException, OrmException, UpdateException,
          IOException {
    CurrentUser user = userProvider.get();
    permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);

    String newChangeMessage =
        createNewChangeMessage(user.asIdentifiedUser().getName(), input.reason);
    DeleteChangeMessageOp deleteChangeMessageOp =
        new DeleteChangeMessageOp(resource.getChangeMessageId(), newChangeMessage);
    try (BatchUpdate batchUpdate =
        updateFactory.create(resource.getChangeResource().getProject(), user, TimeUtil.nowTs())) {
      batchUpdate.addOp(resource.getChangeId(), deleteChangeMessageOp).execute();
    }

    ChangeMessageInfo updatedMessageInfo =
        createUpdatedChangeMessageInfo(resource.getChangeId(), resource.getChangeMessageIndex());
    return Response.created(updatedMessageInfo);
  }

  private ChangeMessageInfo createUpdatedChangeMessageInfo(Change.Id id, int targetIdx)
      throws OrmException, PermissionBackendException {
    List<ChangeMessage> messages = changeMessagesUtil.byChange(notesFactory.createChecked(id));
    ChangeMessage updatedChangeMessage = messages.get(targetIdx);
    AccountLoader accountLoader = accountLoaderFactory.create(true);
    ChangeMessageInfo info = createChangeMessageInfo(updatedChangeMessage, accountLoader);
    accountLoader.fill();
    return info;
  }

  @VisibleForTesting
  public static String createNewChangeMessage(String deletedBy, @Nullable String deletedReason) {
    requireNonNull(deletedBy, "user name must not be null");

    if (Strings.isNullOrEmpty(deletedReason)) {
      return createNewChangeMessage(deletedBy);
    }
    return String.format("Change message removed by: %s\nReason: %s", deletedBy, deletedReason);
  }

  @VisibleForTesting
  public static String createNewChangeMessage(String deletedBy) {
    requireNonNull(deletedBy, "user name must not be null");

    return "Change message removed by: " + deletedBy;
  }

  private class DeleteChangeMessageOp implements BatchUpdateOp {
    private final String targetMessageId;
    private final String newMessage;

    DeleteChangeMessageOp(String targetMessageIdx, String newMessage) {
      this.targetMessageId = targetMessageIdx;
      this.newMessage = newMessage;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) {
      PatchSet.Id psId = ctx.getChange().currentPatchSetId();
      changeMessagesUtil.replaceChangeMessage(ctx.getUpdate(psId), targetMessageId, newMessage);
      return true;
    }
  }

  @Singleton
  public static class DefaultDeleteChangeMessage
      extends RetryingRestModifyView<ChangeMessageResource, Input, Response<ChangeMessageInfo>> {
    private final DeleteChangeMessage deleteChangeMessage;

    @Inject
    public DefaultDeleteChangeMessage(
        DeleteChangeMessage deleteChangeMessage, RetryHelper retryHelper) {
      super(retryHelper);
      this.deleteChangeMessage = deleteChangeMessage;
    }

    @Override
    protected Response<ChangeMessageInfo> applyImpl(
        BatchUpdate.Factory updateFactory, ChangeMessageResource resource, Input input)
        throws Exception {
      return deleteChangeMessage.applyImpl(updateFactory, resource, new DeleteChangeMessageInput());
    }
  }
}
