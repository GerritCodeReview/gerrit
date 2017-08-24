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
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ignore
    implements RestModifyView<ChangeResource, Ignore.Input>, UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Ignore.class);

  public static class Input {}

  private final Provider<IdentifiedUser> self;
  private final StarredChangesUtil stars;

  @Inject
  Ignore(Provider<IdentifiedUser> self, StarredChangesUtil stars) {
    this.self = self;
    this.stars = stars;
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Ignore")
        .setTitle("Ignore the change")
        .setVisible(!rsrc.isUserOwner() && !isIgnored(rsrc));
  }

  @Override
  public Response<String> apply(ChangeResource rsrc, Input input) throws RestApiException {
    try {
      if (rsrc.isUserOwner() || isIgnored(rsrc)) {
        // early exit for own changes and already ignored changes
        return Response.ok("");
      }
      stars.ignore(self.get().getAccountId(), rsrc.getProject(), rsrc.getChange().getId());
    } catch (OrmException e) {
      throw new RestApiException("failed to ignore change", e);
    }
    return Response.ok("");
  }

  private boolean isIgnored(ChangeResource rsrc) {
    try {
      return stars.isIgnoredBy(rsrc.getChange().getId(), self.get().getAccountId());
    } catch (OrmException e) {
      log.error("failed to check ignored star", e);
    }
    return false;
  }
}
