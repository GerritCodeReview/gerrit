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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApprovalTypes {
  protected List<ApprovalType> approvalTypes;
  private transient Map<String, ApprovalType> byId;
  private transient Map<String, ApprovalType> byLabel;

  protected ApprovalTypes() {
  }

  public ApprovalTypes(final List<ApprovalType> approvals) {
    approvalTypes = approvals;
    byId();
  }

  public List<ApprovalType> getApprovalTypes() {
    return approvalTypes;
  }

  public ApprovalType byId(String id) {
    return byId().get(id);
  }

  private Map<String, ApprovalType> byId() {
    if (byId == null) {
      byId = new HashMap<String, ApprovalType>();
      if (approvalTypes != null) {
        for (final ApprovalType t : approvalTypes) {
          byId.put(t.getId(), t);
        }
      }
    }
    return byId;
  }

  public ApprovalType byLabel(String labelName) {
    return byLabel().get(labelName.toLowerCase());
  }

  private Map<String, ApprovalType> byLabel() {
    if (byLabel == null) {
      byLabel = new HashMap<String, ApprovalType>();
      if (approvalTypes != null) {
        for (ApprovalType t : approvalTypes) {
          byLabel.put(t.getName().toLowerCase(), t);
        }
      }
    }
    return byLabel;
  }
}
