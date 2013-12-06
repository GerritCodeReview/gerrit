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
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;

class ChangeManageServiceImpl implements ChangeManageService {
  private final RebaseChangeHandler.Factory rebaseChangeFactory;

  @Inject
  ChangeManageServiceImpl(
      final RebaseChangeHandler.Factory rebaseChangeFactory) {
    this.rebaseChangeFactory = rebaseChangeFactory;
  }

  public void rebaseChange(final PatchSet.Id patchSetId,
      final AsyncCallback<ChangeDetail> callback) {
    rebaseChangeFactory.create(patchSetId).to(callback);
  }
}
