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
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.RevertedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

public class Revert implements RestModifyView<ChangeResource, RevertInput>,
    UiAction<ChangeResource> {
  private final ChangeHooks hooks;
  private final RevertedSender.Factory revertedSenderFactory;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson json;
  private final GitRepositoryManager gitManager;
  private final PersonIdent myIdent;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeInserter.Factory changeInserterFactory;

  @Inject
  Revert(ChangeHooks hooks,
      RevertedSender.Factory revertedSenderFactory,
      final CommitValidators.Factory commitValidatorsFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson json,
      GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      @GerritPersonIdent final PersonIdent myIdent,
      final ChangeInserter.Factory changeInserterFactory) {
    this.hooks = hooks;
    this.revertedSenderFactory = revertedSenderFactory;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.dbProvider = dbProvider;
    this.json = json;
    this.gitManager = gitManager;
    this.myIdent = myIdent;
    this.changeInserterFactory = changeInserterFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
  }

  @Override
  public ChangeInfo apply(ChangeResource req, RevertInput input)
      throws AuthException, ResourceConflictException, IOException,
      NoSuchChangeException, EmailException, OrmException, BadRequestException {
    ChangeControl control = req.getControl();
    Change change = req.getChange();
    if (!control.canAddPatchSet()) {
      throw new AuthException("revert not permitted");
    } else if (change.getStatus() != Status.MERGED) {
      throw new ResourceConflictException("change is " + status(change));
    }

    final Repository git = gitManager.openRepository(control.getProject().getNameKey());
    try {
      CommitValidators commitValidators =
          commitValidatorsFactory.create(control.getRefControl(), new NoSshInfo(), git);

      Change.Id revertedChangeId =
          ChangeUtil.revert(control.getRefControl(), change.currentPatchSetId(),
              (IdentifiedUser) control.getCurrentUser(),
              commitValidators,
              Strings.emptyToNull(input.message), dbProvider.get(),
              revertedSenderFactory, hooks, git, patchSetInfoFactory,
              myIdent, changeInserterFactory);

      return json.format(revertedChangeId);
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } finally {
      git.close();
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
      .setLabel("Revert")
      .setTitle("Revert the change")
      .setVisible(resource.getChange().getStatus() == Status.MERGED
          && resource.getControl().getRefControl().canUpload());
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
   }
 }
