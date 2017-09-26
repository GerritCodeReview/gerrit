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
public class Unmute
    implements RestModifyView<ChangeResource, Unmute.Input>, UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Unmute.class);

  public static class Input {}

  private final StarredChangesUtil stars;

  @Inject
  Unmute(StarredChangesUtil stars) {
    this.stars = stars;
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Unmute")
        .setTitle("Unmute the change")
        .setVisible(isMuted(rsrc));
  }

  @Override
  public Response<String> apply(ChangeResource rsrc, Input input)
      throws OrmException, IllegalLabelException {
    if (isMuted(rsrc)) {
      stars.unmute(rsrc);
    }
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
}
