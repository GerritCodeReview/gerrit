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
import com.google.gerrit.common.data.ChangeManageService;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSet.Id;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.inject.Inject;

class ChangeManageServiceImpl implements ChangeManageService {
  private final RebaseChangeHandler.Factory rebaseChangeFactory;
  private final PublishAction.Factory publishAction;
  private final DeleteDraftChange.Factory deleteDraftChangeFactory;
  private final EditCommitMessageHandler.Factory editCommitMessageHandlerFactory;

  @Inject
  ChangeManageServiceImpl(
      final RebaseChangeHandler.Factory rebaseChangeFactory,
      final PublishAction.Factory publishAction,
      final DeleteDraftChange.Factory deleteDraftChangeFactory,
      final EditCommitMessageHandler.Factory editCommitMessageHandler) {
    this.rebaseChangeFactory = rebaseChangeFactory;
    this.publishAction = publishAction;
    this.deleteDraftChangeFactory = deleteDraftChangeFactory;
    this.editCommitMessageHandlerFactory = editCommitMessageHandler;
  }

  public void rebaseChange(final PatchSet.Id patchSetId,
      final AsyncCallback<ChangeDetail> callback) {
    rebaseChangeFactory.create(patchSetId).to(callback);
  }

  public void publish(final PatchSet.Id patchSetId,
      final AsyncCallback<ChangeDetail> callback) {
    publishAction.create(patchSetId).to(callback);
  }

  public void deleteDraftChange(final PatchSet.Id patchSetId,
      final AsyncCallback<VoidResult> callback) {
    deleteDraftChangeFactory.create(patchSetId).to(callback);
  }

  public void createNewPatchSet(Id patchSetId, String message,
      AsyncCallback<ChangeDetail> callback) {
    editCommitMessageHandlerFactory.create(patchSetId, message).to(callback);
  }
}
