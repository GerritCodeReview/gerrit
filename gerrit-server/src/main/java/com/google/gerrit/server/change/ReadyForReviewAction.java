// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

@RequiresCapability(GlobalCapability.WORK_IN_PROGRESS)
public class ReadyForReviewAction extends BaseWipAction implements
    UiAction<ChangeResource>,
    RestModifyView<ChangeResource, BaseWipAction.Input> {

  @Inject
  ReadyForReviewAction(@GerritServerConfig Config config,
      Provider<ReviewDb> dbProvider,
      Provider<CurrentUser> userProvider) {
    super(config, dbProvider, userProvider);
  }

  @Override
  public Object apply(ChangeResource rsrc, Input input)
      throws BadRequestException, ResourceConflictException, OrmException {
    if (!isWipWorkflowEnabled()) {
      throw new BadRequestException("WIP Workflow is not enabled");
    }

    Change change = rsrc.getChange();
    if (change.getStatus() != Status.WORKINPROGRESS) {
      throw new ResourceConflictException("change is " + status(change));
    }

    changeStatus(change, input, Status.WORKINPROGRESS, Status.NEW);
    return Response.none();
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new Description()
        .setLabel("Ready")
        .setTitle("Set Ready For Review")
        .setVisible(isWipWorkflowEnabled()
           && rsrc.getChange().getStatus() == Status.WORKINPROGRESS);
  }
}
