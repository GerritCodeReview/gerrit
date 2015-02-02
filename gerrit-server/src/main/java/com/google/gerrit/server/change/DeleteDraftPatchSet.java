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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.DeleteDraftPatchSet.Input;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;

@Singleton
public class DeleteDraftPatchSet implements RestModifyView<RevisionResource, Input>,
    UiAction<RevisionResource> {
  public static class Input {
  }

  protected final Provider<ReviewDb> dbProvider;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeUtil changeUtil;
  private final ChangeIndexer indexer;
  private final boolean allowDrafts;

  @Inject
  public DeleteDraftPatchSet(Provider<ReviewDb> dbProvider,
      PatchSetInfoFactory patchSetInfoFactory,
      ChangeUtil changeUtil,
      ChangeIndexer indexer,
      @GerritServerConfig Config cfg) {
    this.dbProvider = dbProvider;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.changeUtil = changeUtil;
    this.indexer = indexer;
    this.allowDrafts = cfg.getBoolean("change", "allowDrafts", true);
  }

  @Override
  public Response<?> apply(RevisionResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException,
      ResourceConflictException, MethodNotAllowedException,
      OrmException, IOException {
    PatchSet patchSet = rsrc.getPatchSet();
    PatchSet.Id patchSetId = patchSet.getId();
    Change change = rsrc.getChange();

    if (!patchSet.isDraft()) {
      throw new ResourceConflictException("Patch set is not a draft");
    }

    if (!allowDrafts) {
      throw new MethodNotAllowedException("Draft workflow is disabled");
    }

    if (!rsrc.getControl().canDeleteDraft(dbProvider.get())) {
      throw new AuthException("Not permitted to delete this draft patch set");
    }

    deleteDraftPatchSet(patchSet, change);
    deleteOrUpdateDraftChange(patchSetId, change);

    return Response.none();
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) {
    try {
      int psCount = dbProvider.get().patchSets()
          .byChange(rsrc.getChange().getId()).toList().size();
      return new UiAction.Description()
        .setTitle(String.format("Delete draft revision %d",
            rsrc.getPatchSet().getPatchSetId()))
        .setVisible(allowDrafts
            && rsrc.getPatchSet().isDraft()
            && rsrc.getControl().canDeleteDraft(dbProvider.get())
            && psCount > 1);
    } catch (OrmException e) {
      throw new IllegalStateException(e);
    }
  }

  private void deleteDraftPatchSet(PatchSet patchSet, Change change)
      throws ResourceNotFoundException, OrmException, IOException {
    try {
      changeUtil.deleteOnlyDraftPatchSet(patchSet, change);
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage());
    }
  }

  private void deleteOrUpdateDraftChange(PatchSet.Id patchSetId,
      Change change) throws OrmException, ResourceNotFoundException,
      IOException {
    if (dbProvider.get()
            .patchSets()
            .byChange(change.getId())
            .toList().size() == 0) {
      deleteDraftChange(change);
    } else {
      if (change.currentPatchSetId().equals(patchSetId)) {
        updateChange(dbProvider.get(), change,
            previousPatchSetInfo(patchSetId));
      } else {
        // TODO(davido): find a better way to enforce cache invalidation.
        updateChange(dbProvider.get(), change, null);
      }
    }
  }

  private void deleteDraftChange(Change change)
      throws OrmException, IOException, ResourceNotFoundException {
    try {
      changeUtil.deleteDraftChange(change);
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage());
    }
  }

  private PatchSetInfo previousPatchSetInfo(PatchSet.Id patchSetId)
      throws ResourceNotFoundException {
    try {
      return patchSetInfoFactory.get(dbProvider.get(),
          new PatchSet.Id(patchSetId.getParentKey(),
              patchSetId.get() - 1));
    } catch (PatchSetInfoNotAvailableException e) {
        throw new ResourceNotFoundException(e.getMessage());
    }
  }

  private void updateChange(final ReviewDb db,
      Change change, final PatchSetInfo psInfo)
      throws OrmException, IOException  {
    change = db.changes().atomicUpdate(change.getId(),
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change c) {
            if (psInfo != null) {
              c.setCurrentPatchSet(psInfo);
            }
            ChangeUtil.updated(c);
            return c;
          }
        });
    indexer.index(db, change);
  }
}
