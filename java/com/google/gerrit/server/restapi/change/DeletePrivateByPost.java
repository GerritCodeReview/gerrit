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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;

import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.update.RetryHelper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DeletePrivateByPost extends DeletePrivate implements UiAction<ChangeResource> {
  @Inject
  DeletePrivateByPost(
      RetryHelper retryHelper,
      ChangeMessagesUtil cmUtil,
      PermissionBackend permissionBackend,
      SetPrivateOp.Factory setPrivateOpFactory) {
    super(retryHelper, cmUtil, permissionBackend, setPrivateOpFactory);
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Unmark private")
        .setTitle("Unmark change as private")
        .setVisible(and(rsrc.getChange().isPrivate(), canDeletePrivate(rsrc)));
  }
}
