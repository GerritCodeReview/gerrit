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

import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.server.changedetail.Submit;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class SubmitAction extends Handler<ChangeDetail> {
  interface Factory {
    SubmitAction create(PatchSet.Id patchSetId);
  }

  private final Submit.Factory submitFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final PatchSet.Id patchSetId;

  @Inject
  SubmitAction(final Submit.Factory submitFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId) {
    this.submitFactory = submitFactory;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
  }

  @Override
  public ChangeDetail call() throws OrmException, NoSuchEntityException,
      IllegalStateException, InvalidChangeOperationException,
      PatchSetInfoNotAvailableException, NoSuchChangeException {
    final ReviewResult result =
        submitFactory.create(patchSetId).call();
    if (result.getErrors().size() > 0) {
      throw new IllegalStateException(
          "Cannot submit " + result.getErrors().get(0).getMessageOrType());
    }
    return changeDetailFactory.create(result.getChangeId()).call();
  }
}
