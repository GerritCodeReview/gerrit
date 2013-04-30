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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.changedetail.RebaseChange;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

import java.io.IOException;

class RebaseChangeHandler extends Handler<ChangeDetail> {
  interface Factory {
    RebaseChangeHandler create(PatchSet.Id patchSetId);
  }

  private final RebaseChange rebaseChange;
  private final IdentifiedUser currentUser;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final PatchSet.Id patchSetId;

  @Inject
  RebaseChangeHandler(final RebaseChange rebaseChange,
      final IdentifiedUser currentUser,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId) {
    this.rebaseChange = rebaseChange;
    this.currentUser = currentUser;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
  }

  @Override
  public ChangeDetail call() throws NoSuchChangeException, OrmException,
      EmailException, NoSuchEntityException, PatchSetInfoNotAvailableException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException, NoSuchProjectException {
    rebaseChange.rebase(patchSetId, currentUser);
    return changeDetailFactory.create(patchSetId.getParentKey()).call();
  }
}
