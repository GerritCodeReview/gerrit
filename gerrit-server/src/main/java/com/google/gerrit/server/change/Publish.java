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

import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.Publish.Input;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.PatchSetNotificationSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;

public class Publish implements RestModifyView<RevisionResource, Input>,
    UiAction<RevisionResource> {
  public static class Input {
  }

  private final Provider<ReviewDb> dbProvider;
  private final ChangeUpdate.Factory updateFactory;
  private final PatchSetNotificationSender sender;
  private final ChangeHooks hooks;
  private final ChangeIndexer indexer;
  private final RevisionEditPublisher editPublisher;
  private final boolean allowDrafts;

  @Inject
  public Publish(Provider<ReviewDb> dbProvider,
      ChangeUpdate.Factory updateFactory,
      PatchSetNotificationSender sender,
      ChangeHooks hooks,
      ChangeIndexer indexer,
      RevisionEditPublisher editPublisher,
      @GerritServerConfig Config cfg) {
    this.dbProvider = dbProvider;
    this.updateFactory = updateFactory;
    this.sender = sender;
    this.hooks = hooks;
    this.indexer = indexer;
    this.editPublisher = editPublisher;
    this.allowDrafts = cfg.getBoolean("change", "allowDrafts", true);
  }

  @Override
  public Response<?> apply(RevisionResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException,
      ResourceConflictException, OrmException, IOException {
    if (!rsrc.getPatchSet().isDraft() && !rsrc.isEdit()) {
      throw new ResourceConflictException(
          "Patch set is not a draft or revision edit");
    }

    if (rsrc.isEdit()) {
      publishRevisionEdit(rsrc);
    } else {
      publishDraft(rsrc);
    }

    return Response.none();
  }

  private void publishDraft(RevisionResource rsrc) throws OrmException,
      AuthException, IOException, ResourceNotFoundException,
      ResourceConflictException {
    if (!hasAcl(rsrc)) {
      throw new AuthException("Cannot publish this draft patch set");
    }

    if (!allowDrafts) {
      throw new ResourceConflictException("Draft workflow is disabled.");
    }

    PatchSet updatedPatchSet = updateDraftPatchSet(rsrc);
    Change updatedChange = updateDraftChange(rsrc);
    ChangeUpdate update = updateFactory.create(rsrc.getControl(),
        updatedChange.getLastUpdatedOn());

    try {
      if (!updatedPatchSet.isDraft()
          || updatedChange.getStatus() == Change.Status.NEW) {
        CheckedFuture<?, IOException> indexFuture =
            indexer.indexAsync(updatedChange.getId());
        hooks.doDraftPublishedHook(updatedChange, updatedPatchSet, dbProvider.get());
        sender.send(rsrc.getNotes(), update,
            rsrc.getChange().getStatus() == Change.Status.DRAFT,
            rsrc.getUser(), updatedChange, updatedPatchSet,
            rsrc.getControl().getLabelTypes());
        indexFuture.checkedGet();
      }
    } catch (PatchSetInfoNotAvailableException e) {
      throw new ResourceNotFoundException(e.getMessage());
    }
  }

  private void publishRevisionEdit(RevisionResource rsrc)
      throws AuthException, ResourceNotFoundException,
      ResourceConflictException, OrmException {
    rsrc.checkEdit();
    Change change = rsrc.getChange();
    PatchSet ps = rsrc.getPatchSet();
    try {
      editPublisher.publish(change, ps);
    } catch (InvalidChangeOperationException | IOException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(change.getId().toString());
    }
  }

  private Change updateDraftChange(RevisionResource rsrc) throws OrmException {
    return dbProvider.get().changes()
        .atomicUpdate(rsrc.getChange().getId(),
        new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        if (change.getStatus() == Change.Status.DRAFT) {
          change.setStatus(Change.Status.NEW);
          ChangeUtil.updated(change);
        }
        return change;
      }
    });
  }

  private PatchSet updateDraftPatchSet(RevisionResource rsrc) throws OrmException {
    return dbProvider.get().patchSets()
        .atomicUpdate(rsrc.getPatchSet().getId(),
        new AtomicUpdate<PatchSet>() {
      @Override
      public PatchSet update(PatchSet patchset) {
        patchset.setDraft(false);
        return patchset;
      }
    });
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) {
    try {
      String title = String.format("Publish %s %s",
          rsrc.getPatchSet().isEdit()
              ? "revision edit"
              : "draft revision",
          rsrc.getPatchSet().getId().getId());
      return new UiAction.Description()
        .setTitle(title)
        .setVisible(
            ((allowDrafts
             && rsrc.getPatchSet().isDraft()
             || rsrc.getPatchSet().isEdit()))
            && hasAcl(rsrc));
    } catch (OrmException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean hasAcl(RevisionResource rsrc) throws OrmException {
    return rsrc.isEdit()
        ? true
        : rsrc.getControl().canPublish(dbProvider.get());
  }

  public static class CurrentRevision implements
      RestModifyView<ChangeResource, Input> {
    private final Provider<ReviewDb> dbProvider;
    private final Publish publish;

    @Inject
    CurrentRevision(Provider<ReviewDb> dbProvider,
        Publish publish) {
      this.dbProvider = dbProvider;
      this.publish = publish;
    }

    @Override
    public Response<?> apply(ChangeResource rsrc, Input input)
        throws AuthException, ResourceConflictException,
        ResourceNotFoundException, IOException, OrmException {
      PatchSet ps = dbProvider.get().patchSets()
        .get(rsrc.getChange().currentPatchSetId());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      } else if (!rsrc.getControl().isPatchVisible(ps, dbProvider.get())) {
        throw new AuthException("current revision not accessible");
      }
      return publish.apply(new RevisionResource(rsrc, ps), input);
    }
  }
}
