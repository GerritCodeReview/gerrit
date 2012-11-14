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
import com.google.gerrit.extensions.restapi.InvalidApiCallException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.Abandon;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import javax.annotation.Nullable;

class AbandonChangeHandler extends Handler<ChangeDetail> {

  interface Factory {
    AbandonChangeHandler create(PatchSet.Id patchSetId, String message);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final Abandon abandon;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final PatchSet.Id patchSetId;
  @Nullable
  private final String message;

  @Inject
  AbandonChangeHandler(final ChangeControl.Factory changeControlFactory,
      final ChangeResource.Factory changeResourceFactory,
      final Abandon abandon,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted @Nullable final String message) {
    this.changeControlFactory = changeControlFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.abandon = abandon;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
    this.message = message;
  }

  @Override
  public ChangeDetail call() throws InvalidApiCallException, Exception {
    final Abandon.Input input = new Abandon.Input();
    input.message = message;
    ChangeControl ctl =
        changeControlFactory.controlFor(patchSetId.getParentKey());
    ChangeResource req = changeResourceFactory.create(ctl);
    final ReviewResult result = (ReviewResult) abandon.apply(req, input);
    if (result.getErrors().size() > 0) {
      throw new NoSuchChangeException(result.getChangeId());
    }
    return changeDetailFactory.create(result.getChangeId()).call();
  }
}
