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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.DeleteDraftChange.Input;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;

public class DeleteDraftChange implements
    RestModifyView<ChangeResource, Input>, UiAction<ChangeResource> {
  public static class Input {
  }

  protected final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager gitManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final ChangeIndexer indexer;

  @Inject
  public DeleteDraftChange(Provider<ReviewDb> dbProvider,
      GitRepositoryManager gitManager,
      GitReferenceUpdated gitRefUpdated,
      PatchSetInfoFactory patchSetInfoFactory,
      ChangeIndexer indexer) {
    this.dbProvider = dbProvider;
    this.gitManager = gitManager;
    this.gitRefUpdated = gitRefUpdated;
    this.indexer = indexer;
  }

  @Override
  public Object apply(ChangeResource rsrc, Input input)
      throws ResourceConflictException, AuthException,
      ResourceNotFoundException, OrmException, IOException {
    if (rsrc.getChange().getStatus() != Status.DRAFT) {
      throw new ResourceConflictException("Change is not a draft");
    }

    if (!rsrc.getControl().canDeleteDraft(dbProvider.get())) {
      throw new AuthException("Not permitted to delete this draft change");
    }

    try {
      ChangeUtil.deleteDraftChange(rsrc.getChange().getId(),
          gitManager, gitRefUpdated, dbProvider.get(), indexer);
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage());
    }

    return Response.none();
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    try {
      return new UiAction.Description()
        .setTitle(String.format("Delete draft change %d",
            rsrc.getChange().getChangeId()))
        .setVisible(rsrc.getChange().getStatus() == Status.DRAFT
            && rsrc.getControl().canDeleteDraft(dbProvider.get()));
    } catch (OrmException e) {
      throw new IllegalStateException(e);
    }
  }
}
