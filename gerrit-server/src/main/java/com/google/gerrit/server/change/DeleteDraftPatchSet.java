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

import com.google.common.collect.Iterables;
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
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.DeleteDraftPatchSet.Input;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.Collections;

@Singleton
public class DeleteDraftPatchSet implements RestModifyView<RevisionResource, Input>,
    UiAction<RevisionResource> {
  public static class Input {
  }

  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory updateFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeUtil changeUtil;
  private final boolean allowDrafts;

  @Inject
  public DeleteDraftPatchSet(Provider<ReviewDb> db,
      BatchUpdate.Factory updateFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      ChangeUtil changeUtil,
      @GerritServerConfig Config cfg) {
    this.db = db;
    this.updateFactory = updateFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.changeUtil = changeUtil;
    this.allowDrafts = cfg.getBoolean("change", "allowDrafts", true);
  }

  @Override
  public Response<?> apply(RevisionResource rsrc, Input input)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu = updateFactory.create(
        db.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      bu.addOp(rsrc.getChange().getId(), new Op(rsrc.getPatchSet().getId()));
      bu.execute();
    }
    return Response.none();
  }

  private class Op extends BatchUpdate.Op {
    private final PatchSet.Id psId;

    private Op(PatchSet.Id psId) {
      this.psId = psId;
    }

    @Override
    public void updateChange(ChangeContext ctx)
        throws RestApiException, OrmException, IOException {
      PatchSet patchSet = ctx.getDb().patchSets().get(psId);
      if (patchSet == null) {
        return; // Nothing to do.
      }
      if (!patchSet.isDraft()) {
        throw new ResourceConflictException("Patch set is not a draft");
      }
      if (!allowDrafts) {
        throw new MethodNotAllowedException("draft workflow is disabled");
      }
      if (!ctx.getChangeControl().canDeleteDraft(ctx.getDb())) {
        throw new AuthException("Not permitted to delete this draft patch set");
      }

      deleteDraftPatchSet(patchSet, ctx);
      deleteOrUpdateDraftChange(ctx);
    }

    private void deleteDraftPatchSet(PatchSet patchSet, ChangeContext ctx)
        throws ResourceNotFoundException, OrmException, IOException {
      try {
        changeUtil.deleteOnlyDraftPatchSet(patchSet, ctx.getChange());
      } catch (NoSuchChangeException e) {
        throw new ResourceNotFoundException(e.getMessage());
      }
    }

    private void deleteOrUpdateDraftChange(ChangeContext ctx)
        throws OrmException, ResourceNotFoundException, IOException {
      Change c = ctx.getChange();
      if (Iterables.isEmpty(ctx.getDb().patchSets().byChange(c.getId()))) {
        deleteDraftChange(c);
        ctx.markDeleted();
        return;
      }
      if (c.currentPatchSetId().equals(psId)) {
        c.setCurrentPatchSet(previousPatchSetInfo(ctx));
      }
      ChangeUtil.updated(c);
      ctx.getDb().changes().update(Collections.singleton(c));
    }

    private void deleteDraftChange(Change change)
        throws OrmException, IOException, ResourceNotFoundException {
      try {
        changeUtil.deleteDraftChange(change);
      } catch (NoSuchChangeException e) {
        throw new ResourceNotFoundException(e.getMessage());
      }
    }

    private PatchSetInfo previousPatchSetInfo(ChangeContext ctx)
        throws OrmException {
      try {
        // TODO(dborowitz): Get this in a way that doesn't involve re-opening
        // the repo after the updateRepo phase.
        return patchSetInfoFactory.get(ctx.getDb(),
            new PatchSet.Id(psId.getParentKey(), psId.get() - 1));
      } catch (PatchSetInfoNotAvailableException e) {
        throw new OrmException(e);
      }
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) {
    try {
      int psCount = db.get().patchSets()
          .byChange(rsrc.getChange().getId()).toList().size();
      return new UiAction.Description()
        .setTitle(String.format("Delete draft revision %d",
            rsrc.getPatchSet().getPatchSetId()))
        .setVisible(allowDrafts
            && rsrc.getPatchSet().isDraft()
            && rsrc.getControl().canDeleteDraft(db.get())
            && psCount > 1);
    } catch (OrmException e) {
      throw new IllegalStateException(e);
    }
  }
}
