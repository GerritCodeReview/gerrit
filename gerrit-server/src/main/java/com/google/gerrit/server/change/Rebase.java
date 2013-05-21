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

import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.Rebase.Input;
import com.google.gerrit.server.changedetail.RebaseChange;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;

public class Rebase implements RestModifyView<RevisionResource, Input> {
  public static class Input {
  }

  private final RebaseChange rebaseChange;
  private final ChangeJson json;

  @Inject
  public Rebase(RebaseChange rebaseChange, ChangeJson json) {
    this.rebaseChange = rebaseChange;
    this.json = json;
  }

  @Override
  public ChangeInfo apply(RevisionResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException,
      ResourceConflictException, EmailException, OrmException {
    ChangeControl control = rsrc.getControl();
    Change change = rsrc.getChange();
    if (!control.canRebase()) {
      throw new AuthException("rebase not permitted");
    } else if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is "
          + change.getStatus().name().toLowerCase());
    }

    try {
      rebaseChange.rebase(rsrc.getPatchSet().getId(), rsrc.getUser());
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (IOException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(change.getId().toString());
    }

    json.addOption(ListChangesOption.CURRENT_REVISION)
        .addOption(ListChangesOption.CURRENT_COMMIT);
    return json.format(change.getId());
  }

  public static class CurrentRevision implements
      RestModifyView<ChangeResource, Input> {
    private final Provider<ReviewDb> dbProvider;
    private final Rebase rebase;

    @Inject
    CurrentRevision(Provider<ReviewDb> dbProvider, Rebase rebase) {
      this.dbProvider = dbProvider;
      this.rebase = rebase;
    }

    @Override
    public ChangeInfo apply(ChangeResource rsrc, Input input)
        throws AuthException, ResourceNotFoundException,
        ResourceConflictException, EmailException, OrmException {
      PatchSet ps =
          dbProvider.get().patchSets()
              .get(rsrc.getChange().currentPatchSetId());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      } else if (!rsrc.getControl().isPatchVisible(ps, dbProvider.get())) {
        throw new AuthException("current revision not accessible");
      }
      return rebase.apply(new RevisionResource(rsrc, ps), input);
    }
  }
}
