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
import com.google.gerrit.reviewdb.PatchSetApproval;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Summarizes the approvals (or negative approvals) for a patch set.
 * This will typically contain zero or one approvals for each
 * category, with all of the approvals coming from a single patch set.
 */
public class ApprovalSummary {
  protected Map<ApprovalCategory.Id, PatchSetApproval> approvals;

  protected ApprovalSummary() {
  }

  public ApprovalSummary(final Iterable<PatchSetApproval> list) {
    approvals = new HashMap<ApprovalCategory.Id, PatchSetApproval>();
    for (final PatchSetApproval a : list) {
      approvals.put(a.getCategoryId(), a);
    }
  }

  public Map<ApprovalCategory.Id, PatchSetApproval> getApprovalMap() {
    return Collections.unmodifiableMap(approvals);
  }
}
