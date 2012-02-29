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

import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ChangeDetailService;
import com.google.gerrit.common.data.IncludedInDetail;
import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.common.data.PatchSetPublishDetail;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;

class ChangeDetailServiceImpl implements ChangeDetailService {
  private final ChangeDetailFactory.Factory changeDetail;
  private final IncludedInDetailFactory.Factory includedInDetail;
  private final PatchSetDetailFactory.Factory patchSetDetail;
  private final PatchSetPublishDetailFactory.Factory patchSetPublishDetail;
  private final ListDestinationBranches.Factory listDestinationBranchesFactory;

  @Inject
  ChangeDetailServiceImpl(final ChangeDetailFactory.Factory changeDetail,
      final IncludedInDetailFactory.Factory includedInDetail,
      final PatchSetDetailFactory.Factory patchSetDetail,
      final PatchSetPublishDetailFactory.Factory patchSetPublishDetail,
      final ListDestinationBranches.Factory listDestinationBranchesFactory) {
    this.changeDetail = changeDetail;
    this.includedInDetail = includedInDetail;
    this.patchSetDetail = patchSetDetail;
    this.patchSetPublishDetail = patchSetPublishDetail;
    this.listDestinationBranchesFactory = listDestinationBranchesFactory;
  }

  public void changeDetail(final Change.Id id,
      final AsyncCallback<ChangeDetail> callback) {
    changeDetail.create(id).to(callback);
  }

  public void includedInDetail(final Change.Id id,
      final AsyncCallback<IncludedInDetail> callback) {
    includedInDetail.create(id).to(callback);
  }

  public void patchSetDetail(PatchSet.Id id,
      AsyncCallback<PatchSetDetail> callback) {
    patchSetDetail2(null, id, null, callback);
  }

  public void patchSetDetail2(PatchSet.Id baseId, PatchSet.Id id,
      AccountDiffPreference diffPrefs, AsyncCallback<PatchSetDetail> callback) {
    patchSetDetail.create(baseId, id, diffPrefs).to(callback);
  }

  public void patchSetPublishDetail(final PatchSet.Id id,
      final AsyncCallback<PatchSetPublishDetail> callback) {
    patchSetPublishDetail.create(id).to(callback);
  }

  public void listDestinationBranches(final Project.NameKey projectName,
      final AsyncCallback<ListBranchesResult> callback) {
    listDestinationBranchesFactory.create(projectName).to(callback);
  }
}
