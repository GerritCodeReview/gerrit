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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.ApprovalCategory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApprovalTypes {
  protected List<ApprovalType> approvalTypes;
  protected List<ApprovalType> actionTypes;
  private transient Map<ApprovalCategory.Id, ApprovalType> byCategoryId;

  protected ApprovalTypes() {
  }

  public ApprovalTypes(final List<ApprovalType> approvals,
      final List<ApprovalType> actions) {
    approvalTypes = approvals;
    actionTypes = actions;
    byCategory();
  }

  public List<ApprovalType> getApprovalTypes() {
    return approvalTypes;
  }

  public List<ApprovalType> getActionTypes() {
    return actionTypes;
  }

  public ApprovalType getApprovalType(final ApprovalCategory.Id id) {
    return byCategory().get(id);
  }

  public Set<ApprovalCategory.Id> getApprovalCategories() {
    return byCategory().keySet();
  }

  private Map<ApprovalCategory.Id, ApprovalType> byCategory() {
    if (byCategoryId == null) {
      byCategoryId = new HashMap<ApprovalCategory.Id, ApprovalType>();
      if (actionTypes != null) {
        for (final ApprovalType t : actionTypes) {
          byCategoryId.put(t.getCategory().getId(), t);
        }
      }

      if (approvalTypes != null) {
        for (final ApprovalType t : approvalTypes) {
          byCategoryId.put(t.getCategory().getId(), t);
        }
      }
    }
    return byCategoryId;
  }
}
