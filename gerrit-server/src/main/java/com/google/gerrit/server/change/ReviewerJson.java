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

package com.google.gerrit.server.change;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ReviewerJson {
  private final Provider<ReviewDb> db;
  private final ApprovalTypes approvalTypes;
  private final FunctionState.Factory functionState;
  private final AccountInfo.Loader.Factory accountLoaderFactory;

  @Inject
  ReviewerJson(Provider<ReviewDb> db,
      ApprovalTypes approvalTypes,
      FunctionState.Factory functionState,
      AccountInfo.Loader.Factory accountLoaderFactory) {
    this.db = db;
    this.approvalTypes = approvalTypes;
    this.functionState = functionState;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  public List<ReviewerInfo> format(Collection<ReviewerResource> rsrcs) throws OrmException {
    List<ReviewerInfo> infos = Lists.newArrayListWithCapacity(rsrcs.size());
    AccountInfo.Loader loader = accountLoaderFactory.create(true);
    for (ReviewerResource rsrc : rsrcs) {
      ReviewerInfo info = format(rsrc, null);
      loader.put(info);
      infos.add(info);
    }
    loader.fill();
    return infos;
  }

  public List<ReviewerInfo> format(ReviewerResource rsrc) throws OrmException {
    return format(ImmutableList.<ReviewerResource> of(rsrc));
  }

  public ReviewerInfo format(ReviewerInfo out, ChangeControl control,
      List<PatchSetApproval> approvals) throws OrmException {
    PatchSet.Id psId = control.getChange().currentPatchSetId();

    if (approvals == null) {
      approvals = db.get().patchSetApprovals()
          .byPatchSetUser(psId, out._id).toList();
    }

    FunctionState fs = functionState.create(control, psId, approvals);
    for (ApprovalType at : approvalTypes.getApprovalTypes()) {
      CategoryFunction.forCategory(at.getCategory()).run(at, fs);
    }

    out.approvals = Maps.newHashMapWithExpectedSize(approvals.size());
    for (PatchSetApproval ca : approvals) {
      for (PermissionRange pr : control.getLabelRanges()) {
        if (pr.getMin() != 0 || pr.getMax() != 0) {
          // TODO: Support arbitrary labels.
          ApprovalType at = approvalTypes.byId(ca.getCategoryId());
          if (at != null) {
            out.approvals.put(at.getCategory().getLabelName(),
                ApprovalCategoryValue.formatValue(ca.getValue()));
          }
        }
      }
    }
    if (out.approvals.isEmpty()) {
      out.approvals = null;
    }

    return out;
  }

  private ReviewerInfo format(ReviewerResource rsrc,
      List<PatchSetApproval> approvals) throws OrmException {
    return format(new ReviewerInfo(rsrc.getUser().getAccountId()),
        rsrc.getUserControl(), approvals);
  }

  public static class ReviewerInfo extends AccountInfo {
    final String kind = "gerritcodereview#reviewer";
    Map<String, String> approvals;

    protected ReviewerInfo(Account.Id id) {
      super(id);
    }
  }

  public static class PutResult {
    List<ReviewerInfo> reviewers;
    Boolean confirm;
    String error;
  }
}
