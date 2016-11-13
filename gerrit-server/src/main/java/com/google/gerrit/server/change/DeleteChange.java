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

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.DeleteChange.Input;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class DeleteChange
    implements RestModifyView<ChangeResource, Input>, UiAction<ChangeResource> {
  public static class Input {}

  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory updateFactory;
  private final Provider<DeleteChangeOp> opProvider;
  private final boolean allowDrafts;

  @Inject
  public DeleteChange(
      Provider<ReviewDb> db,
      BatchUpdate.Factory updateFactory,
      Provider<DeleteChangeOp> opProvider,
      @GerritServerConfig Config cfg) {
    this.db = db;
    this.updateFactory = updateFactory;
    this.opProvider = opProvider;
    this.allowDrafts = DeleteChangeOp.allowDrafts(cfg);
  }

  @Override
  public Response<?> apply(ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu =
        updateFactory.create(db.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Change.Id id = rsrc.getChange().getId();
      bu.setOrder(BatchUpdate.Order.DB_BEFORE_REPO);
      bu.addOp(id, opProvider.get());
      bu.execute();
    }
    return Response.none();
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    try {
      Change.Status status = rsrc.getChange().getStatus();
      ChangeControl changeControl = rsrc.getControl();
      boolean visible =
          isActionAllowed(changeControl, status) && changeControl.canDelete(db.get(), status);
      return new UiAction.Description()
          .setLabel("Delete")
          .setTitle("Delete change " + rsrc.getId())
          .setVisible(visible);
    } catch (OrmException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean isActionAllowed(ChangeControl changeControl, Status status) {
    return status != Status.DRAFT || allowDrafts || changeControl.isAdmin();
  }
}
