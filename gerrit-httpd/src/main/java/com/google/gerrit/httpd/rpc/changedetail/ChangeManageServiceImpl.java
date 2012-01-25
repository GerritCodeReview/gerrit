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
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;

class ChangeManageServiceImpl implements ChangeManageService {
  private final SubmitAction.Factory submitAction;
  private final AbandonChangeHandler.Factory abandonChangeHandlerFactory;
  private final RebaseChange.Factory rebaseChangeFactory;
  private final RestoreChangeHandler.Factory restoreChangeHandlerFactory;
  private final RevertChange.Factory revertChangeFactory;
  private final PublishAction.Factory publishAction;
  private final DeleteDraftChange.Factory deleteDraftChangeFactory;

  @Inject
  ChangeManageServiceImpl(final SubmitAction.Factory patchSetAction,
      final AbandonChangeHandler.Factory abandonChangeHandlerFactory,
      final RebaseChange.Factory rebaseChangeFactory,
      final RestoreChangeHandler.Factory restoreChangeHandlerFactory,
      final RevertChange.Factory revertChangeFactory,
      final PublishAction.Factory publishAction,
      final DeleteDraftChange.Factory deleteDraftChangeFactory) {
    this.submitAction = patchSetAction;
    this.abandonChangeHandlerFactory = abandonChangeHandlerFactory;
    this.rebaseChangeFactory = rebaseChangeFactory;
    this.restoreChangeHandlerFactory = restoreChangeHandlerFactory;
    this.revertChangeFactory = revertChangeFactory;
    this.publishAction = publishAction;
    this.deleteDraftChangeFactory = deleteDraftChangeFactory;
  }

  public void submit(final PatchSet.Id patchSetId,
      final AsyncCallback<ChangeDetail> cb) {
    submitAction.create(patchSetId).to(cb);
  }

  public void abandonChange(final PatchSet.Id patchSetId, final String message,
      final AsyncCallback<ChangeDetail> callback) {
    abandonChangeHandlerFactory.create(patchSetId, message).to(callback);
  }

  public void rebaseChange(final PatchSet.Id patchSetId,
      final AsyncCallback<ChangeDetail> callback) {
    rebaseChangeFactory.create(patchSetId).to(callback);
  }

  public void revertChange(final PatchSet.Id patchSetId, final String message,
      final AsyncCallback<ChangeDetail> callback) {
    revertChangeFactory.create(patchSetId, message).to(callback);
  }

  public void restoreChange(final PatchSet.Id patchSetId, final String message,
      final AsyncCallback<ChangeDetail> callback) {
    restoreChangeHandlerFactory.create(patchSetId, message).to(callback);
  }

  public void publish(final PatchSet.Id patchSetId,
      final AsyncCallback<ChangeDetail> callback) {
    publishAction.create(patchSetId).to(callback);
  }

  public void deleteDraftChange(final PatchSet.Id patchSetId,
      final AsyncCallback<VoidResult> callback) {
    deleteDraftChangeFactory.create(patchSetId).to(callback);
  }
}
