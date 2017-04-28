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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Unmute
    implements RestModifyView<ChangeResource, Unmute.Input>, UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Unmute.class);

  public static class Input {}

  private final Provider<IdentifiedUser> self;
  private final StarredChangesUtil stars;

  @Inject
  Unmute(Provider<IdentifiedUser> self, StarredChangesUtil stars) {
    this.self = self;
    this.stars = stars;
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Unmute")
        .setTitle("Unmute the change")
        .setVisible(!rsrc.isUserOwner() && isUnMuteable(rsrc.getChange()));
  }

  @Override
  public Response<String> apply(ChangeResource rsrc, Input input) throws RestApiException {
    try {
      if (rsrc.isUserOwner() || !isMuted(rsrc.getChange())) {
        // early exit for own changes and not muted changes
        return Response.ok("");
      }
      stars.unmute(self.get().getAccountId(), rsrc.getProject(), rsrc.getChange());
    } catch (OrmException e) {
      throw new RestApiException("failed to unmute change", e);
    }
    return Response.ok("");
  }

  private boolean isMuted(Change change) {
    try {
      return stars.isMutedBy(change, self.get().getAccountId());
    } catch (OrmException e) {
      log.error("failed to check muted star", e);
    }
    return false;
  }

  private boolean isUnMuteable(Change change) {
    try {
      return isMuted(change) && !stars.isIgnoredBy(change.getId(), self.get().getAccountId());
    } catch (OrmException e) {
      log.error("failed to check ignored star", e);
    }
    return false;
  }
}
