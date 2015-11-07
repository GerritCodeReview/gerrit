// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.common.data.ChangeDetailService;
import com.google.gerrit.common.data.DiffType;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;

class ChangeDetailServiceImpl implements ChangeDetailService {
  private final PatchSetDetailFactory.Factory patchSetDetail;

  @Inject
  ChangeDetailServiceImpl(
      final PatchSetDetailFactory.Factory patchSetDetail) {
    this.patchSetDetail = patchSetDetail;
  }

  @Override
  public void patchSetDetail(PatchSet.Id id, DiffType diffType,
      AsyncCallback<PatchSetDetail> callback) {
    patchSetDetail2(null, id, null, diffType, callback);
  }

  @Override
  public void patchSetDetail2(PatchSet.Id baseId, PatchSet.Id id,
      DiffPreferencesInfo diffPrefs, DiffType diffType,
      AsyncCallback<PatchSetDetail> callback) {
    patchSetDetail.create(baseId, id, diffPrefs, diffType).to(callback);
  }
}
