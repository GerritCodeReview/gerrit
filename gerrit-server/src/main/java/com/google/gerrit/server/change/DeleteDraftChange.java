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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.DeleteDraftChange.Input;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;

@Singleton
public class DeleteDraftChange implements
    RestModifyView<ChangeResource, Input>, UiAction<ChangeResource> {
  public static class Input {
  }

  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory updateFactory;
  private final ChangeUtil changeUtil;
  private final boolean allowDrafts;

  @Inject
  public DeleteDraftChange(Provider<ReviewDb> db,
      BatchUpdate.Factory updateFactory,
      ChangeUtil changeUtil,
      @GerritServerConfig Config cfg) {
    this.db = db;
    this.updateFactory = updateFactory;
    this.changeUtil = changeUtil;
    this.allowDrafts = cfg.getBoolean("change", "allowDrafts", true);
  }

  @Override
  public Response<?> apply(ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu = updateFactory.create(
        db.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Change.Id id = rsrc.getChange().getId();
      bu.addOp(id, new Op());
      bu.execute();
    }
    return Response.none();
  }

  private class Op extends BatchUpdate.Op {
    @Override
    public void updateChange(ChangeContext ctx)
        throws RestApiException, OrmException, IOException {
      if (ctx.getChange().getStatus() != Status.DRAFT) {
        throw new ResourceConflictException("Change is not a draft");
      }

      if (!ctx.getChangeControl().canDeleteDraft(ctx.getDb())) {
        throw new AuthException("Not permitted to delete this draft change");
      }

      if (!allowDrafts) {
        throw new MethodNotAllowedException("draft workflow is disabled");
      }

      try {
        changeUtil.deleteDraftChange(ctx.getChange());
        ctx.markDeleted();
      } catch (NoSuchChangeException e) {
        throw new ResourceNotFoundException(e.getMessage());
      }
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    try {
      return new UiAction.Description()
        .setTitle("Delete draft change " + rsrc.getId())
        .setVisible(allowDrafts
            && rsrc.getChange().getStatus() == Status.DRAFT
            && rsrc.getControl().canDeleteDraft(db.get()));
    } catch (OrmException e) {
      throw new IllegalStateException(e);
    }
  }
}
