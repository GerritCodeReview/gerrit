// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.Revert.Input;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.RevertedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.PersonIdent;

public class Revert implements RestModifyView<ChangeResource, Input> {
  private final ChangeHooks hooks;
  private final RevertedSender.Factory revertedSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson json;
  private final GitRepositoryManager gitManager;
  private final PersonIdent myIdent;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final GitReferenceUpdated replication;

  public static class Input {
    public String message;
  }

  @Inject
  Revert(ChangeHooks hooks,
      RevertedSender.Factory revertedSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson json,
      GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final GitReferenceUpdated replication,
      @GerritPersonIdent final PersonIdent myIdent) {
    this.hooks = hooks;
    this.revertedSenderFactory = revertedSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
    this.gitManager = gitManager;
    this.myIdent = myIdent;
    this.replication = replication;
    this.patchSetInfoFactory = patchSetInfoFactory;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(ChangeResource req, Input input)
      throws Exception {
    ChangeControl control = req.getControl();
    Change change = req.getChange();
    if (!control.canAddPatchSet()) {
      throw new NoSuchChangeException(change.getId());
    } else if (change.getStatus() != Status.MERGED) {
      throw new ResourceConflictException("change is " + status(change));
    }

    final String message = Strings.emptyToNull(input.message);
    final PatchSet.Id patchSetId = change.currentPatchSetId();
    final ReviewDb db = dbProvider.get();
    IdentifiedUser currentUser = (IdentifiedUser) control.getCurrentUser();

    Change.Id revertedChangeId = ChangeUtil.revert(patchSetId, currentUser, message, db,
        revertedSenderFactory, hooks, gitManager, patchSetInfoFactory,
        replication, myIdent);

    return json.format(revertedChangeId);
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
   }
 }
