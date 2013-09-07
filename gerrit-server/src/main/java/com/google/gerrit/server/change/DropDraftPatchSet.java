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

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.DropDraftPatchSet.Input;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class DropDraftPatchSet implements RestModifyView<RevisionResource, Input>,
    UiAction<RevisionResource> {
  public static class Input {
  }

  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager gitManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final PatchSetInfoFactory patchSetInfoFactory;

  @Inject
  public DropDraftPatchSet(Provider<ReviewDb> dbProvider,
      GitRepositoryManager gitManager,
      GitReferenceUpdated gitRefUpdated,
      PatchSetInfoFactory patchSetInfoFactory) {
    this.dbProvider = dbProvider;
    this.gitManager = gitManager;
    this.gitRefUpdated = gitRefUpdated;
    this.patchSetInfoFactory = patchSetInfoFactory;
  }

  @Override
  public Object apply(RevisionResource rsrc, Input input)
      throws NoSuchChangeException, OrmException, IOException {
    final Change change = rsrc.getChange();
    PatchSet.Id patchSetId = rsrc.getPatchSet().getId();
    ChangeUtil.deleteOnlyDraftPatchSet(rsrc.getPatchSet(),
          change, gitManager, gitRefUpdated, dbProvider.get());
    List<PatchSet> restOfPatches = dbProvider.get().patchSets()
        .byChange(change.getId()).toList();
    if (restOfPatches.size() == 0) {
        ChangeUtil.deleteDraftChange(patchSetId,
            gitManager, gitRefUpdated, dbProvider.get());
    } else {
      PatchSet.Id highestId = null;
      for (PatchSet ps : restOfPatches) {
        if (highestId == null || ps.getPatchSetId() > highestId.get()) {
          highestId = ps.getId();
        }
      }
      if (change.currentPatchSetId().equals(patchSetId)) {
        try {
          PatchSet.Id id =
              new PatchSet.Id(patchSetId.getParentKey(), patchSetId.get() - 1);
          change.setCurrentPatchSet(patchSetInfoFactory
              .get(dbProvider.get(), id));
        } catch (PatchSetInfoNotAvailableException e) {
          throw new NoSuchChangeException(change.getId());
        }
        dbProvider.get().changes().update(Collections.singleton(change));
      }
    }
    return Response.none();
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    PatchSet.Id current = resource.getChange().currentPatchSetId();
    try {
      int psCount = dbProvider.get().patchSets()
          .byChange(resource.getChange().getId()).toList().size();
      return new UiAction.Description()
        .setLabel(String.format("Delete Draft Revision %d",
            resource.getPatchSet().getPatchSetId()))
        .setVisible(resource.getPatchSet().isDraft()
            && resource.getPatchSet().getId().equals(current)
            && resource.getControl().canDeleteDraft(dbProvider.get())
            && psCount > 1);
    } catch (OrmException e) {
      throw new IllegalStateException(e);
    }
  }
}
