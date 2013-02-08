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

import com.google.common.collect.Maps;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;
import java.util.Map;

public class ReviewerJson {
  private final Provider<ReviewDb> db;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeControl.GenericFactory controlFactory;
  private final ApprovalTypes approvalTypes;
  private final FunctionState.Factory functionState;

  @Inject
  ReviewerJson(Provider<ReviewDb> db,
      IdentifiedUser.GenericFactory userFactory,
      ChangeControl.GenericFactory controlFactory,
      ApprovalTypes approvalTypes,
      FunctionState.Factory functionState) {
    this.db = db;
    this.userFactory = userFactory;
    this.controlFactory = controlFactory;
    this.approvalTypes = approvalTypes;
    this.functionState = functionState;
  }

  public ReviewerInfo format(ReviewerResource rsrc) throws OrmException,
      NoSuchChangeException {
    ReviewerInfo out = new ReviewerInfo();
    Account account = rsrc.getAccount();
    out.id = account.getId().toString();
    out.email = account.getPreferredEmail();
    out.name = account.getFullName();

    Change change = rsrc.getChange();
    PatchSet.Id psId = change.currentPatchSetId();

    List<PatchSetApproval> approvals = db.get().patchSetApprovals()
        .byPatchSetUser(psId, account.getId()).toList();

    // TODO: Support arbitrary labels.
    ChangeControl control = controlFactory.controlFor(change,
        userFactory.create(account.getId()));
    FunctionState fs = functionState.create(control, psId, approvals);
    for (ApprovalType at : approvalTypes.getApprovalTypes()) {
      CategoryFunction.forCategory(at.getCategory()).run(at, fs);
    }

    out.approvals = Maps.newHashMapWithExpectedSize(approvals.size());
    for (PatchSetApproval ca : approvals) {
      for (PermissionRange pr : control.getLabelRanges()) {
        if (pr.getMin() != 0 || pr.getMax() != 0) {
          ApprovalType at = approvalTypes.byId(ca.getCategoryId());
          out.approvals.put(at.getCategory().getLabelName(),
              ApprovalCategoryValue.formatValue(ca.getValue()));
        }
      }
    }

    return out;
  }

  public static class ReviewerInfo {
    final String kind = "gerritcodereview#reviewer";
    String id;
    String email;
    String name;

    Map<String, String> approvals;
  }
}
