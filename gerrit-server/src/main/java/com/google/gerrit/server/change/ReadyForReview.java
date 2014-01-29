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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;

class ReadyForReview extends ReworkAction implements
    UiAction<ChangeResource>,
    RestModifyView<ChangeResource, ReworkAction.Input> {
  private final boolean allowWip;

  @Inject
  ReadyForReview(Provider<ReviewDb> dbProvider,
      Provider<CurrentUser> userProvider,
      ChangeIndexer indexer,
      @GerritServerConfig Config cfg) {
    super(dbProvider, userProvider, indexer);
    this.allowWip = ReworkStrategy.isWip(cfg);
  }

  @Override
  public Object apply(ChangeResource rsrc, Input input)
      throws ResourceConflictException, OrmException, IOException,
      BadRequestException {
    if (!allowWip) {
      throw new BadRequestException("WIP workflow is disabled.");
    }

    Change change = rsrc.getChange();
    if (change.getStatus() != Status.DRAFT) {
      throw new ResourceConflictException("change is " + status(change));
    }
    changeStatus(change, input, Status.DRAFT, Status.NEW);
    return Response.none();
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new Description()
        .setLabel("Ready")
        .setTitle("Set Ready For Review")
        .setVisible(allowWip
           && rsrc.getControl().isOwner()
           && rsrc.getChange().getStatus() == Status.DRAFT);
  }
}
