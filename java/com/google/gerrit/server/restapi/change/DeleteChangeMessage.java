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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.DeleteChangeMessageInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountDirectory;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.List;

/** Deletes a change message by rewriting commit history. */
@Singleton
public class DeleteChangeMessage
    extends RetryingRestModifyView<
        ChangeResource, DeleteChangeMessageInput, Response<ChangeMessageInfo>> {

  private final Provider<CurrentUser> userProvider;
  private final Provider<ReviewDb> dbProvider;
  private final PermissionBackend permissionBackend;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  public DeleteChangeMessage(
      Provider<CurrentUser> userProvider,
      Provider<ReviewDb> dbProvider,
      PermissionBackend permissionBackend,
      ChangeMessagesUtil changeMessagesUtil,
      AccountLoader.Factory accountLoaderFactory,
      RetryHelper retryHelper) {
    super(retryHelper);
    this.userProvider = userProvider;
    this.dbProvider = dbProvider;
    this.permissionBackend = permissionBackend;
    this.changeMessagesUtil = changeMessagesUtil;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  public Response<ChangeMessageInfo> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource resource, DeleteChangeMessageInput input)
      throws Exception {
    CurrentUser user = userProvider.get();
    permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);

    if (input == null || input.id == null || input.id.isEmpty()) {
      throw new BadRequestException("change message uuid is required");
    }

    List<ChangeMessage> messages =
        changeMessagesUtil.byChange(dbProvider.get(), resource.getNotes());
    int targetMessageIdx = -1;
    for (int i = 0; i < messages.size(); ++i) {
      if (messages.get(i).getKey().get().equals(input.id)) {
        targetMessageIdx = i;
        break;
      }
    }

    if (targetMessageIdx < 0) {
      throw new ResourceNotFoundException(String.format("change message %s not found", input.id));
    }

    String newChangeMessage =
        createNewChangeMessage(user.asIdentifiedUser().getName(), input.reason);
    DeleteChangeMessageOp deleteChangeMessageOp =
        new DeleteChangeMessageOp(targetMessageIdx, newChangeMessage);
    try (BatchUpdate batchUpdate =
        updateFactory.create(dbProvider.get(), resource.getProject(), user, TimeUtil.nowTs())) {
      batchUpdate.addOp(resource.getId(), deleteChangeMessageOp).execute();
    }

    ChangeMessageInfo updatedMessageInfo =
        createUpdatedChangeMessageInfo(resource, targetMessageIdx);
    return Response.created(updatedMessageInfo);
  }

  private ChangeMessageInfo createUpdatedChangeMessageInfo(ChangeResource resource, int targetIdx)
      throws OrmException {
    List<ChangeMessage> messages =
        changeMessagesUtil.byChange(dbProvider.get(), resource.getNotes());
    ChangeMessage updatedChangeMessage = messages.get(targetIdx);
    AccountLoader accountLoader =
        accountLoaderFactory.create(
            EnumSet.of(
                AccountDirectory.FillOptions.ID,
                AccountDirectory.FillOptions.NAME,
                AccountDirectory.FillOptions.USERNAME));
    ChangeMessageInfo info = createChangeMessageInfo(updatedChangeMessage, accountLoader);
    accountLoader.fill();
    return info;
  }

  @VisibleForTesting
  public static String createNewChangeMessage(String deletedBy, String deletedReason) {
    if (Strings.isNullOrEmpty(deletedReason)) {
      return createNewChangeMessage(deletedBy);
    }
    return String.format("Change message removed by: %s; Reason: %s", deletedBy, deletedReason);
  }

  @VisibleForTesting
  public static String createNewChangeMessage(String deletedBy) {
    return "Change message removed by: " + deletedBy;
  }

  private class DeleteChangeMessageOp implements BatchUpdateOp {
    // The UUID of a change message could be different between NoteDb and ReviewDb. In NoteDb, it's
    // the commit ObjectId, but in ReviewDb it's generated randomly. To make sure the change message
    // could be deleted from both NoteDb and ReviewDb, the index of the change message could be used
    // rather than its UUID.
    private final int targetMessageIdx;
    private final String newMessage;

    DeleteChangeMessageOp(int targetMessageIdx, String newMessage) {
      this.targetMessageIdx = targetMessageIdx;
      this.newMessage = newMessage;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws OrmException {
      PatchSet.Id psId = ctx.getChange().currentPatchSetId();
      changeMessagesUtil.deleteChangeMessageByRewritingHistory(
          ctx.getDb(), ctx.getUpdate(psId), targetMessageIdx, newMessage);
      return true;
    }
  }
}
