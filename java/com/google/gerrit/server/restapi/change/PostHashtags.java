// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.SetHashtagsOp;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PostHashtags
    extends RetryingRestModifyView<
        ChangeResource, HashtagsInput, Response<ImmutableSortedSet<String>>>
    implements UiAction<ChangeResource> {
  private final SetHashtagsOp.Factory hashtagsFactory;

  @Inject
  PostHashtags(RetryHelper retryHelper, SetHashtagsOp.Factory hashtagsFactory) {
    super(retryHelper);
    this.hashtagsFactory = hashtagsFactory;
  }

  @Override
  protected Response<ImmutableSortedSet<String>> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource req, HashtagsInput input)
      throws RestApiException, UpdateException, PermissionBackendException {
    req.permissions().check(ChangePermission.EDIT_HASHTAGS);

    try (BatchUpdate bu =
        updateFactory.create(req.getChange().getProject(), req.getUser(), TimeUtil.nowTs())) {
      SetHashtagsOp op = hashtagsFactory.create(input);
      bu.addOp(req.getId(), op);
      bu.execute();
      return Response.ok(op.getUpdatedHashtags());
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Edit Hashtags")
        .setVisible(rsrc.permissions().testCond(ChangePermission.EDIT_HASHTAGS));
  }
}
