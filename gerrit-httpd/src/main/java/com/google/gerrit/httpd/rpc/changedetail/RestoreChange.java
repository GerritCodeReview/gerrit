// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.*;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import javax.annotation.Nullable;

class RestoreChange extends Handler<ChangeDetail> {
  interface Factory {
    RestoreChange create(PatchSet.Id patchSetId, String message);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final AbandonedSender.Factory abandonedSenderFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final PatchSet.Id patchSetId;
  @Nullable
  private final String message;

  private final ChangeHookRunner hooks;

  @Inject
  RestoreChange(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final IdentifiedUser currentUser,
      final AbandonedSender.Factory abandonedSenderFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted @Nullable final String message, final ChangeHookRunner hooks) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
    this.message = message;
    this.hooks = hooks;
  }

  @Override
  public ChangeDetail call() throws NoSuchChangeException, OrmException,
      EmailException, NoSuchEntityException, PatchSetInfoNotAvailableException {

    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    if (!control.canRestore()) {
      throw new NoSuchChangeException(changeId);
    }

    ChangeUtil.restore(patchSetId, currentUser, message, db,
       abandonedSenderFactory, hooks);

    return changeDetailFactory.create(changeId).call();
  }
}
