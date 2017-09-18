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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.StarredChangesUtil.IllegalLabelException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Mute implements RestModifyView<ChangeResource, Mute.Input>, UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Mute.class);

  public static class Input {}

  private final StarredChangesUtil stars;

  @Inject
  Mute(StarredChangesUtil stars) {
    this.stars = stars;
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Mute")
        .setTitle("Mute the change to unhighlight it in the dashboard")
        .setVisible(!rsrc.isUserOwner() && isMuteable(rsrc));
  }

  @Override
  public Response<String> apply(ChangeResource rsrc, Input input)
      throws RestApiException, OrmException, IllegalLabelException {
    if (rsrc.isUserOwner() || isMuted(rsrc)) {
      // early exit for own changes and already muted changes
      return Response.ok("");
    }
    stars.mute(rsrc);
    return Response.ok("");
  }

  private boolean isMuted(ChangeResource rsrc) {
    try {
      return stars.isMuted(rsrc);
    } catch (OrmException e) {
      log.error("failed to check muted star", e);
    }
    return false;
  }

  private boolean isMuteable(ChangeResource rsrc) {
    try {
      return !isMuted(rsrc) && !stars.isIgnored(rsrc);
    } catch (OrmException e) {
      log.error("failed to check ignored star", e);
    }
    return false;
  }
}
