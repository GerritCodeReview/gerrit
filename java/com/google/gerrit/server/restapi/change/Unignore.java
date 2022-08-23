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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.StarredChangesUtil.IllegalLabelException;
import com.google.gerrit.server.change.ChangeResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class Unignore implements RestModifyView<ChangeResource, Input>, UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final StarredChangesUtil stars;

  @Inject
  Unignore(StarredChangesUtil stars) {
    this.stars = stars;
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Unignore")
        .setTitle("Unignore the change")
        .setVisible(isIgnored(rsrc));
  }

  @Override
  public Response<String> apply(ChangeResource rsrc, Input input) throws IllegalLabelException {
    if (isIgnored(rsrc)) {
      stars.unignore(rsrc);
    }
    return Response.ok();
  }

  private boolean isIgnored(ChangeResource rsrc) {
    try {
      return stars.isIgnored(rsrc);
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log("failed to check ignored star");
    }
    return false;
  }
}
