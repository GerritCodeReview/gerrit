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

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.changedetail.RebaseChange;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class Rebase implements RestModifyView<RevisionResource, RebaseInput>,
    UiAction<RevisionResource> {

  private static final Logger log =
      LoggerFactory.getLogger(PatchSetInserter.class);

  private final Provider<RebaseChange> rebaseChange;
  private final ChangeJson json;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  public Rebase(Provider<RebaseChange> rebaseChange, ChangeJson json,
      Provider<ReviewDb> dbProvider) {
    this.rebaseChange = rebaseChange;
    this.json = json
        .addOption(ListChangesOption.CURRENT_REVISION)
        .addOption(ListChangesOption.CURRENT_COMMIT);
    this.dbProvider = dbProvider;
  }

  @Override
  public ChangeInfo apply(RevisionResource rsrc, RebaseInput input)
      throws AuthException, ResourceNotFoundException,
      ResourceConflictException, EmailException, OrmException {
    ChangeControl control = rsrc.getControl();
    Change change = rsrc.getChange();
    if (!control.canRebase()) {
      throw new AuthException("rebase not permitted");
    } else if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is "
          + change.getStatus().name().toLowerCase());
    } else if (!hasOneParent(rsrc.getPatchSet().getId())) {
      throw new ResourceConflictException(
          "cannot rebase merge commits or commit with no ancestor");
    }

    String baseRev = null;
    if (input != null && input.base != null) {
      String base = input.base.trim();
      do {
        if (base.equals("")) {
          // remove existing dependency to other patch set
          baseRev = change.getDest().get();
          break;
        }

        ReviewDb db = dbProvider.get();
        PatchSet basePatchSet = parseBase(base);
        if (basePatchSet == null) {
          throw new ResourceConflictException("base revision is missing: " + base);
        } else if (!rsrc.getControl().isPatchVisible(basePatchSet, db)) {
          throw new AuthException("base revision not accessible: " + base);
        } else if (change.getId().equals(basePatchSet.getId().getParentKey())) {
          throw new ResourceConflictException("cannot depend on self");
        }

        Change baseChange = db.changes().get(basePatchSet.getId().getParentKey());
        if (baseChange != null) {
          if (!baseChange.getProject().equals(change.getProject())) {
            throw new ResourceConflictException("base change is in wrong project: "
                                                + baseChange.getProject());
          } else if (!baseChange.getDest().equals(change.getDest())) {
            throw new ResourceConflictException("base change is targetting wrong branch: "
                                                + baseChange.getDest());
          } else if (baseChange.getStatus() == Status.ABANDONED) {
            throw new ResourceConflictException("base change is abandoned: "
                                                + baseChange.getKey());
          }
          baseRev = basePatchSet.getRevision().get();
          break;
        }
      } while (false);  // just wanted to use the break statement
    }

    try {
      rebaseChange.get().rebase(rsrc.getChange(), rsrc.getPatchSet().getId(),
          rsrc.getUser(), baseRev);
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (IOException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(change.getId().toString());
    }

    return json.format(change.getId());
  }

  private PatchSet parseBase(final String base) throws OrmException {
    ReviewDb db = dbProvider.get();

    PatchSet.Id basePatchSetId = PatchSet.Id.fromRef(base);
    if (basePatchSetId != null) {
      // try parsing the base as a ref string
      return db.patchSets().get(basePatchSetId);
    }

    // try parsing base as a change number (assume current patch set)
    PatchSet basePatchSet = null;
    try {
      Change.Id baseChangeId = Change.Id.parse(base);
      if (baseChangeId != null) {
        for (PatchSet ps : db.patchSets().byChange(baseChangeId)) {
          if (basePatchSet == null || basePatchSet.getId().get() < ps.getId().get()){
            basePatchSet = ps;
          }
        }
      }
    } catch (NumberFormatException e) {  // probably a SHA1
    }

    // try parsing as SHA1
    if (basePatchSet == null) {
      for (PatchSet ps : db.patchSets().byRevision(new RevId(base))) {
        if (basePatchSet == null || basePatchSet.getId().get() < ps.getId().get()) {
          basePatchSet = ps;
        }
      }
    }

    return basePatchSet;
  }

  private boolean hasOneParent(final PatchSet.Id patchSetId) {
    try {
      // prevent rebase of exotic changes (merge commit, no ancestor).
      return (dbProvider.get().patchSetAncestors()
          .ancestorsOf(patchSetId).toList().size() == 1);
    } catch (OrmException e) {
      log.error("Failed to get ancestors of patch set "
          + patchSetId.toRefName(), e);
      return false;
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    return new UiAction.Description()
      .setLabel("Rebase")
      .setTitle("Rebase onto tip of branch or parent change")
      .setVisible(resource.getChange().getStatus().isOpen()
          && resource.getControl().canRebase()
          && hasOneParent(resource.getPatchSet().getId()));
  }

  public static class CurrentRevision implements
      RestModifyView<ChangeResource, RebaseInput> {
    private final Rebase rebase;

    @Inject
    CurrentRevision(Rebase rebase) {
      this.rebase = rebase;
    }

    @Override
    public ChangeInfo apply(ChangeResource rsrc, RebaseInput input)
        throws AuthException, ResourceNotFoundException,
        ResourceConflictException, EmailException, OrmException {
      PatchSet ps =
          rebase.dbProvider.get().patchSets()
              .get(rsrc.getChange().currentPatchSetId());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      } else if (!rsrc.getControl().isPatchVisible(ps, rebase.dbProvider.get())) {
        throw new AuthException("current revision not accessible");
      }
      return rebase.apply(new RevisionResource(rsrc, ps), input);
    }
  }
}
