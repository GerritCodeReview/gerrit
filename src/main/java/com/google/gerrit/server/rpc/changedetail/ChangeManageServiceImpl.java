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

package com.google.gerrit.server.rpc.changedetail;

import com.google.gerrit.client.changes.ChangeManageService;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;

class ChangeManageServiceImpl implements ChangeManageService {
  private final SubmitAction.Factory submitAction;

  @Inject
  ChangeManageServiceImpl(final SubmitAction.Factory patchSetAction) {
    this.submitAction = patchSetAction;
  }

  public void patchSetAction(final ApprovalCategoryValue.Id value,
      final PatchSet.Id patchSetId, final AsyncCallback<VoidResult> cb) {
    final ApprovalCategory.Id category = value.getParentKey();
    if (ApprovalCategory.SUBMIT.equals(category) && value.get() == 1) {
      submitAction.create(patchSetId).to(cb);

    } else {
      cb.onFailure(new IllegalArgumentException(value + " not supported"));
    }
  }
}
