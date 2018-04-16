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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.changes.DeleteChangeMessageInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/** Deletes a change message by rewriting commit history. */
@Singleton
public class DeleteChangeMessage
    extends RetryingRestModifyView<ChangeResource, DeleteChangeMessageInput, Response<ChangeInfo>> {

  private final Provider<CurrentUser> userProvider;
  private final PermissionBackend permissionBackend;
  private final ChangeJson.Factory jsonFactory;

  @Inject
  public DeleteChangeMessage(
      Provider<CurrentUser> userProvider,
      PermissionBackend permissionBackend,
      ChangeJson.Factory jsonFactory,
      RetryHelper retryHelper) {
    super(retryHelper);
    this.userProvider = userProvider;
    this.permissionBackend = permissionBackend;
    this.jsonFactory = jsonFactory;
  }

  @Override
  public Response<ChangeInfo> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource resource, DeleteChangeMessageInput input)
      throws Exception {
    CurrentUser user = userProvider.get();
    permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);

    ChangeJson json = jsonFactory.noOptions();
    return Response.created(json.format(resource.getChange()));
  }

  @VisibleForTesting
  public static String getNewChangeMessage(String deletedBy, String deletedReason) {
    if (Strings.isNullOrEmpty(deletedReason)) {
      return getNewChangeMessage(deletedBy);
    }
    return String.format("Change message removed by: %s; Reason: " + deletedBy, deletedReason);
  }

  @VisibleForTesting
  public static String getNewChangeMessage(String deletedBy) {
    return "Change message removed by: " + deletedBy;
  }
}
